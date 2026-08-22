/*
 * v0.63.0: the journal typography. Two voices:
 *
 *   - [JournalSerif] = Crimson Pro — body, headings, bang commands.
 *     Warm, calligraphic, designed for sustained reading on
 *     paper. The drafts use the italic for body copy ("The
 *     light through the window...") and the upright for
 *     headings ("TODAY", "Settings"). Light weight (300) on
 *     the headings, Normal (400) for body, with two italic
 *     faces (300, 400) for the journal entries and the
 *     response text on the mood screen.
 *
 *   - [JournalSans] = Plus Jakarta Sans — small caps, dates,
 *     section labels, the "ARCHIVE / ALL ENTRIES" breadcrumb,
 *     the "How does it feel right now?" subtitle. The drafts
 *     use it for everything the eye reads as "system" rather
 *     than "soft content". Three weights (300, 400, 500) and
 *     tracking 0.25em for the small-caps section heads.
 *
 * Why two voices: the launcher already separates system from
 * soft content with Inter (system) and Lora (soft). The
 * journal needs the same split, but with a serif voice that
 * reads as "intimate" rather than "literary" — Crimson Pro
 * at light weight is the design equivalent of a journal's
 * own handwriting, while Lora at the same weight reads as
 * "I am a book". Crimson Pro is the journal. Plus Jakarta
 * Sans is the desk it sits on.
 *
 * The 1:1 swap from Inter/Lora is intentional. The drafts
 * specify these exact faces. Falling back to the existing
 * CalmSans/CalmSerif would have been cheaper, but the
 * "warmer journal" direction is named for the warmth, and
 * the warmth comes from the Crimson Pro italic.
 */
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
@file:Suppress("MagicNumber")

package org.mindanchor.journal

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import org.mindanchor.R

private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val CrimsonPro = GoogleFont("Crimson Pro")
private val PlusJakartaSans = GoogleFont("Plus Jakarta Sans")

/**
 * The journal voice. Light + Normal + two italics.
 * The 300/400 italic pair matches the draft's "note body"
 * voice (the prose a person writes on a quiet afternoon).
 */
val JournalSerif: FontFamily = FontFamily(
    Font(googleFont = CrimsonPro, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = CrimsonPro, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = CrimsonPro, fontProvider = provider, weight = FontWeight.Light, style = FontStyle.Italic),
    Font(googleFont = CrimsonPro, fontProvider = provider, weight = FontWeight.Normal, style = FontStyle.Italic),
)

/**
 * The desk voice. Light + Normal + Medium for the small-caps
 * section heads. Plus Jakarta Sans is the launcher's system
 * face for the journal — a humanist sans designed for UI.
 */
val JournalSans: FontFamily = FontFamily(
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = PlusJakartaSans, fontProvider = provider, weight = FontWeight.Medium),
)

/**
 * Section heads: 10px, 0.25em tracking, uppercase. Used by the
 * archive breadcrumb, the settings sections, the footer nav,
 * the date subtitles, the "saved quietly" caption. Plus
 * Jakarta Sans at 400 — the design uses 500/600 in places,
 * but the diff is invisible at 10px and 400 holds the line
 * weight on slow-sky backgrounds.
 */
val JournalSmallCaps: TextStyle = TextStyle(
    fontFamily = JournalSans,
    fontWeight = FontWeight.Normal,
    fontSize = 10.sp,
    letterSpacing = 2.5.sp, // 0.25em
)

/** Footer bang commands. Serif italic, 13px, resting at 60% terracotta. */
val JournalBang: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Normal,
    fontSize = 13.sp,
)

/** Quick-note hint row. Sans, 11px, no caps, no italic. */
val JournalHint: TextStyle = TextStyle(
    fontFamily = JournalSans,
    fontWeight = FontWeight.Normal,
    fontSize = 11.sp,
    letterSpacing = 0.6.sp,
)

/**
 * Note body. Serif italic 18px, 1.8 leading. The drafts
 * use this for the journal entries on Today and Archive —
 * the 1.8 line height is what gives the draft its "room
 * to breathe" feel; a 1.5 line height would have read
 * as "blog post", not "diary".
 */
val JournalNoteBody: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Light,
    fontSize = 18.sp,
    lineHeight = 32.4.sp, // 1.8 of 18
)

/**
 * Note body on the archive list — slightly tighter leading
 * (1.9 of 17) and 17px, so four entries fit on a phone
 * without the page becoming a one-entry-per-screen.
 */
val JournalArchiveEntry: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Light,
    fontSize = 17.sp,
    lineHeight = 32.3.sp, // 1.9 of 17
)

/**
 * Quick-note composer textarea. Serif light 20px on
 * small phones, 22px on wider. Italic placeholder only.
 */
val JournalQuickNoteBody: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontWeight = FontWeight.Light,
    fontSize = 20.sp,
    lineHeight = 36.sp, // 1.8 of 20
)

/** Settings row label. Serif normal 17px. */
val JournalSettingsLabel: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 17.sp,
)

/** Mood response line under the mood buttons. Serif italic 20px. */
val JournalMoodResponse: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontStyle = FontStyle.Italic,
    fontWeight = FontWeight.Light,
    fontSize = 20.sp,
)

/** Mood button label. Serif normal 24px. */
val JournalMoodLabel: TextStyle = TextStyle(
    fontFamily = JournalSerif,
    fontWeight = FontWeight.Normal,
    fontSize = 24.sp,
)

/** Mood date subtitle under the mood title. Sans light 11px, wide tracking. */
val JournalDate: TextStyle = TextStyle(
    fontFamily = JournalSans,
    fontWeight = FontWeight.Light,
    fontSize = 11.sp,
    letterSpacing = 2.sp,
)

/** Entry number ("Entry No. 412") on the Today header. Small caps, very faint. */
val JournalEntryNumber: TextStyle = TextStyle(
    fontFamily = JournalSans,
    fontWeight = FontWeight.Light,
    fontSize = 10.sp,
    letterSpacing = 2.5.sp,
)

/** Vertical timestamp in the left margin. Sans 10px, very faint, rotated. */
val JournalTimestampMargin: TextStyle = TextStyle(
    fontFamily = JournalSans,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.sp,
    letterSpacing = -0.2.sp,
)
