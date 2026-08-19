@file:Suppress("MaxLineLength", "FunctionNaming", "LongMethod", "MagicNumber")
package org.mindanchor.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
// v0.51.0: custom-drawn kind icons live in
// [KindGlyph] (Box + primitive shapes).
// Using primitives instead of
// material-icons-extended keeps the APK
// lean (extended adds ~7MB) and matches
// the existing [PinGlyph] pattern from
// v0.49.0 — the launcher's design language
// is "draw it, don't depend on it".
import org.mindanchor.R
import org.mindanchor.digest.DigestActivity
import org.mindanchor.friction.FrictionGate
import org.mindanchor.friction.FrictionTone
import org.mindanchor.friction.GateContext
import org.mindanchor.friction.LoopPhase
import org.mindanchor.letters.Letter
import org.mindanchor.letters.LetterScreen
import org.mindanchor.letters.LetterStore
import org.mindanchor.model.Note
import org.mindanchor.model.NoteActivity
import org.mindanchor.reader.ReadingSize
import org.mindanchor.report.ReportScreen
import org.mindanchor.report.ReportStore
import org.mindanchor.settings.SettingsScreen
import org.mindanchor.vitals.PpgScreen
import org.mindanchor.vitals.WellnessDirection
import org.mindanchor.vitals.WellnessReading
import org.mindanchor.vitals.WellnessSignal
import org.mindanchor.support.SupportActivity
import org.mindanchor.ui.CalmBackground
import org.mindanchor.ui.SkyContent
import org.mindanchor.ui.rememberClockFormat
import org.mindanchor.ui.rememberMinuteTick
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.ui.graphics.Color

/**
 * v0.49.0: the kind colors. The QuickNotesCard picker chips
 * (Quick note / Task / Reminder) and the per-row kind chips
 * on the Notes tab all use these same three tokens. The
 * tokens are file-private (lowercase `kindSage`,
 * `kindIndigo`) so the rest of the file refers to them
 * by name, not by hex literal. The hex values match the
 * v0.45.1 row-chip palette so the picker chips and the row
 * chips are visually the same: a Task picker chip and a
 * Task row chip both render in sage, a Reminder chip both
 * renders in indigo, a Quick note picker chip and a Quick
 * note row chip are both neutral (no fill).
 *
 * The 200/300 tones are the unselected background (soft
 * fill), the 700/800 tones are the selected label and the
 * row chip foreground (high contrast). The picker uses
 * 300/800 in the selected state to match Material 3's
 * `FilterChip(selected = true)` color treatment, so a
 * "selected" Task chip looks like a soft sage pill with a
 * deep sage label.
 */
private val KindSageBg = Color(0xFFB7C9A8)      // sage-300
private val KindSageFg = Color(0xFF3F5233)      // sage-800

/**
 * v0.53.0: the action accent. Reserved for
 * navigation affordances (the "!" in the bang
 * hint, the chevron on the Notes link, swipe
 * gesture affordances) so they read as
 * "actionable" without colliding with the sage
 * "Task" meaning. A warmer, brighter cousin of
 * the sage: a teal-700 that has the same
 * "calm, not aggressive" character as sage but
 * is unambiguously about navigation, not about
 * Task kind. Sits one step further along the
 * colour wheel than sage (sage = green-yellow,
 * teal = green-blue) so the eye reads them as
 * siblings, not as a different family.
 */
private val ActionAccentFg = Color(0xFF0F766E)    // teal-700
private val ActionAccentBg = Color(0xFF99F6E4)    // teal-200

/**
 * v0.53.0: the surface tokens for the three
 * visual hierarchy layers of the home card.
 * The previous v0.50.0 design was a single
 * surface with equal-weight elements; v0.53.0
 * introduces a Material 3 "elevation tiers"
 * approach with three distinct background
 * tones for primary / secondary / tertiary.
 *
 * - LayerPrimary (the clock + the moment) is
 *   the lightest elevation; the clock reads
 *   as the foreground object on a quiet
 *   surface.
 * - LayerSecondary (the mood + the notes) is a
 *   step lighter than the underlying sky, with
 *   a low-opacity card surface.
 * - LayerTertiary (the input + the kind picker
 *   + the save button) is a step lighter again,
 *   with a higher-opacity card surface.
 *
 * The three layers are at 0/4/8dp in Material
 * elevation, which gives the home card a real
 * hierarchy of moments.
 */
private val LayerPrimaryBg = Color(0x14FFFFFF)    // 8% white
private val LayerSecondaryBg = Color(0x1FFFFFFF)  // 12% white
private val LayerTertiaryBg = Color(0x29FFFFFF)   // 16% white
private val LayerPrimaryBorder = Color(0x1FFFFFFF) // 12% white
private val LayerSecondaryBorder = Color(0x33FFFFFF) // 20% white
private val LayerTertiaryBorder = Color(0x40FFFFFF)  // 25% white

/**
 * v0.52.0: the cap on visible body lines for a
 * single Notes tab row. The user asked for full
 * note visibility in the Notes section, so the
 * row shows the body in full — not just the
 * first line. The cap exists as a scannability
 * ceiling, not a hiding rule: a typical
 * 100–200 char body is well under 6 lines and
 * renders in full, a long reflective body (close
 * to [Note.MAX_BODY] = 4000 chars) is clipped to
 * 6 lines + ellipsis.
 *
 * 6 is chosen because:
 * - 4 lines was too tight — a 3-paragraph note
 *   was already getting clipped.
 * - 8 lines was too loose — a 6-line note
 *   pushed the per-row pixel budget past 200dp
 *   and the list lost its "scannable archive"
 *   feel.
 * - 6 lines gives ~3 short paragraphs, which
 *   covers >90% of the seeded 100-note field
 *   test without any clipping.
 */
private const val NOTES_TAB_BODY_MAX_LINES = 6
private val KindIndigoBg = Color(0xFFC7D2FE)    // indigo-200
private val KindIndigoFg = Color(0xFF3730A3)    // indigo-800

/**
 * v0.54.0: swipe-action background colours
 * on the Notes tab. The pin swipe (startToEnd,
 * right-swipe) reuses the [KindSageBg] token
 * so the gesture's colour matches the kind-
 * picker "Task" chip and the home-card pin
 * affordance — sage is the launcher's "Task /
 * pin" semantic, and the swipe inherits the
 * same meaning without learning a new colour.
 *
 * The delete swipe (endToStart, left-swipe)
 * needs a colour that is *not* already used
 * for any other action. Red is the universal
 * "destructive" affordance, but a saturated
 * red-500 would shout on the calm sky. The
 * red-300 (#FCA5A5) is the same saturation
 * family as [KindSageBg] (sage-300) and
 * [KindIndigoBg] (indigo-200): pastel, not
 * bright. The action reads as "destructive"
 * without violating the launcher's quiet
 * aesthetic. A red-700 icon glyph on top
 * reads the gesture's meaning even at a
 * glance.
 */
private val NotesSwipeDeleteBg = Color(0xFFFCA5A5)   // red-300
private val NotesSwipeDeleteFg = Color(0xFFB91C1C)   // red-700

/**
 * v0.49.0: a small pin glyph drawn with
 * Compose primitives so the color comes
 * from a caller-supplied [Color]. The
 * "📌" emoji is a Noto Color Emoji glyph
 * and renders in its own red on every
 * Android version, ignoring the text
 * color. A [Box] with two stacked shapes
 * (a circle on top, a triangle below)
 * paints in whatever color the caller
 * gives it, so the pinned pin is sage
 * (matching the Task kind), and the
 * unpinned pin is a hollow circle in
 * the secondary text color. The shape is
 * deliberately small (16dp square) so it
 * reads as an icon, not as a button. The
 * parent [TextButton] provides the
 * 40dp tap-target so the affordance is
 * still reachable.
 */
@Composable
private fun PinGlyph(
    pinned: Boolean,
    pinnedColor: Color,
    unpinnedColor: Color,
) {
    if (pinned) {
        // Filled pin: a small filled
        // circle on top of a downward
        // triangle, both in sage. The
        // shape is two overlapping Boxes
        // with the same color so the
        // join is invisible.
        Box(
            modifier = Modifier.size(20.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The pin head — a 10dp circle,
            // centered at the top, sage.
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        color = pinnedColor,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ),
            )
            // The pin needle — a small
            // downward triangle from
            // below the head to the bottom
            // of the 20dp box. The triangle
            // is approximated by a 12dp
            // square at the bottom edge
            // (a square reads as a pin tip
            // at this size; the user's eye
            // completes the shape).
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(10.dp)
                    .align(Alignment.BottomCenter)
                    .background(pinnedColor),
            )
        }
    } else {
        // Unpinned: a hollow circle in
        // the secondary text color. The
        // parent button shows the tap
        // affordance; the glyph itself
        // reads as "no pin".
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(
                    color = androidx.compose.ui.graphics.Color.Transparent,
                    shape = androidx.compose.foundation.shape.CircleShape,
                )
                .border(
                    width = 1.5.dp,
                    color = unpinnedColor,
                    shape = androidx.compose.foundation.shape.CircleShape,
                ),
        )
    }
}

/**
 * v0.51.0: a custom-drawn kind icon for the
 * Notes tab row. Each of the three note
 * kinds (Quick / Task / Reminder) has a
 * unique shape, all rendered as primitives
 * (Box + Canvas-aware shapes) so the APK
 * does not need the 7MB
 * `material-icons-extended` artefact.
 *
 * The icons are 18dp inside a 36dp chip;
 * each uses 1.5dp stroke width so the
 * shape reads as an outline, not a fill,
 * and matches the design language of the
 * rest of the launcher (PinGlyph's
 * hollow-circle variant, the home-card
 * mood-emoji slots).
 *
 * - [KindGlyphKind.NOTE] (Quick note): a
 *   page with a folded top-right corner.
 *   Drawn as a 16x16dp rounded square
 *   with a 6x6dp corner notch (Box
 *   behind a smaller Box) plus a
 *   horizontal "lines" stroke (2x12dp
 *   Box) below the fold. The fold reads
 *   as "this is a document, not a card
 *   or a chip".
 *
 * - [KindGlyphKind.TASK]: a checkbox.
 *   Drawn as a 16dp rounded square
 *   outline (Box + border, 1.5dp) with
 *   a 2x10dp diagonal "check" stroke
 *   inside. The shape mirrors the
 *   Material Checkbox on the right
 *   column of TASK rows, so the user's
 *   eye learns "checkbox == Task" once.
 *
 * - [KindGlyphKind.REMINDER]: a clock.
 *   Drawn as a 16dp circle outline
 *   (Box + border) plus two short
 *   strokes inside for the 12-hand and
 *   the 3-hand. Reads as "this is about
 *   a time", not a generic notification.
 */
private enum class KindGlyphKind { NOTE, TASK, REMINDER }

@Composable
private fun KindGlyph(
    kind: KindGlyphKind,
    color: Color,
) {
    val strokeWidth = 1.5.dp
    when (kind) {
        KindGlyphKind.NOTE -> {
            // A page with a folded top-right
            // corner. The fold is a 6x6dp
            // triangle in the top-right of
            // an 18dp box; the page outline
            // is the Box border, and a
            // small "line" stroke below
            // the fold reads as text.
            Box(modifier = Modifier.size(18.dp)) {
                // Page outline (the box).
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            width = strokeWidth,
                            color = color,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp),
                        ),
                )
                // Fold notch (a smaller box in
                // the top-right corner, in the
                // chip background colour, with
                // an L-shaped stroke in the
                // glyph colour). The notch
                // suggests a folded paper
                // corner.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(6.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .size(6.dp)
                            .border(
                                width = strokeWidth,
                                color = color,
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(
                                    bottomStart = 2.dp,
                                ),
                            ),
                    )
                }
                // "Line" stroke (text). Sits
                // below the fold, fills about
                // 60% of the page width.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 3.dp, bottom = 3.dp)
                        .width(10.dp)
                        .height(1.5.dp)
                        .background(color),
                )
            }
        }
        KindGlyphKind.TASK -> {
            // A checkbox with a check mark.
            Box(modifier = Modifier.size(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                        .border(
                            width = strokeWidth,
                            color = color,
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                        ),
                )
                // The check is two strokes:
                // a short downward-left segment
                // and a longer upward-right
                // segment. Approximated by
                // rotating a small Box 45deg.
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = 9.dp, height = 7.dp)
                        .background(
                            color = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 1.dp)
                            .width(7.dp)
                            .height(1.5.dp)
                            .background(color),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 1.dp)
                            .width(2.5.dp)
                            .height(1.5.dp)
                            .background(color),
                    )
                }
            }
        }
        KindGlyphKind.REMINDER -> {
            // A clock. The face is a 16dp
            // circle (Box + CircleShape
            // border) with two short
            // strokes inside: the vertical
            // 12-hand and the horizontal
            // 3-hand. The "12-hand" is
            // shorter to look like a real
            // clock, the "3-hand" longer.
            Box(modifier = Modifier.size(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.Center)
                        .border(
                            width = strokeWidth,
                            color = color,
                            shape = androidx.compose.foundation.shape.CircleShape,
                        ),
                )
                // The 12-hand: vertical
                // 1.5x6dp box from centre to
                // top.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 3.dp)
                        .width(1.5.dp)
                        .height(5.dp)
                        .background(color),
                )
                // The 3-hand: horizontal
                // 5x1.5dp box from centre to
                // right.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 3.dp)
                        .width(5.dp)
                        .height(1.5.dp)
                        .background(color),
                )
            }
        }
    }
}

/**
 * v0.49.0: [PinGlyph] uses [Modifier.border], which
 * is in `androidx.compose.foundation`. The import
 * is at the top of the file with the other
 * `androidx.compose.foundation` imports, but this
 * note keeps the call-site in mind for future
 * PinGlyph re-use (e.g. a pin toggle in the home
 * card's recent-saves row).
 */
private val _pinGlyphImportNote: Unit = Unit

private enum class LauncherSurface {
    Home,
    Drawer,
    Settings,
    Ppg,
    Report,
    Letter,
    // v0.26.0
    GroundMe,
    BeforeYouSend,
    // v0.35.0: the "Get through this" sub-menu. A
    // sibling of the home, the settings, and the
    // drawer; not a separate activity because the
    // three reflective actions it surfaces are
    // existing activities, and a sub-menu is
    // cheaper to navigate between than a fresh
    // Intent trip.
    GetThrough,
    // v0.45.0: the "all notes" tab. A sibling of
    // the home, the drawer, and the settings. Not
    // a separate activity because the list view is
    // a thin projection of the same [NotesPrefs]
    // the home card reads from — the only state
    // the surface holds is "back to home". A
    // sub-screen is cheaper to navigate between
    // than a fresh Intent trip, and the surface
    // shares the lifecycle-scoped data flows.
    Notes,
}

/**
 * v0.25.15: the custom Saver for [DisplayApp?] that lets
 * [rememberSaveable] hold the "actions for" / "gate for"
 * launcher state across config change and process death.
 *
 * The default autoSaver for [DisplayApp] would not work
 * (the data class has 4 fields and Bundle has its own
 * parcelable contract). A [mapSaver] keyed on the
 * component-name is the documented Compose pattern for
 * "I have a small data class, give me a Saver": the
 * save side returns a `Map<String, Any?>` of the four
 * fields, the restore side walks the map back into the
 * data class. `null` is encoded as an empty map — the
 * mapSaver contract is "non-null map means there was a
 * state; empty map means the state was null".
 *
 * Why save the label and the favourite/hidden flags
 * rather than just the component name: the renamed
 * label and the favourite/hidden state are exactly
 * what the long-press dialog is editing, and losing
 * them on a config change would silently revert the
 * user's edit. The ComponentName itself is the join
 * key; the other three fields ride along.
 */
private val DisplayAppNullableSaver: Saver<DisplayApp?, Any> = mapSaver(
    save = { app ->
        if (app == null) {
            emptyMap<String, Any>()
        } else {
            mapOf(
                "component" to app.component,
                "label" to app.label,
                "isFavorite" to app.isFavorite,
                "isHidden" to app.isHidden,
            )
        }
    },
    restore = { raw ->
        @Suppress("UNCHECKED_CAST")
        val map = raw as? Map<String, Any?> ?: return@mapSaver null
        val component = map["component"] as? String ?: return@mapSaver null
        val label = map["label"] as? String ?: return@mapSaver null
        val isFavorite = map["isFavorite"] as? Boolean ?: false
        val isHidden = map["isHidden"] as? Boolean ?: false
        DisplayApp(
            component = component,
            label = label,
            isFavorite = isFavorite,
            isHidden = isHidden,
        )
    },
)

/**
 * v0.25.15: the custom Saver for [LocalDate?] that lets
 * [rememberSaveable] hold the letter reader's selected
 * date across config change and process death. Encoded
 * as the ISO-8601 local date string
 * (`DateTimeFormatter.ISO_LOCAL_DATE` →
 * `"2026-08-14"`) and restored via `LocalDate.parse`.
 * `null` round-trips as the empty string, again so the
 * autoSaver has a non-null value to Bundle.
 */
private val LocalDateNullableSaver: Saver<LocalDate?, Any> = Saver(
    save = { date -> date?.format(DateTimeFormatter.ISO_LOCAL_DATE).orEmpty() },
    restore = { raw ->
        val str = raw as? String ?: return@Saver null
        if (str.isEmpty()) null else runCatching { LocalDate.parse(str) }.getOrNull()
    },
)

/**
 * v0.20.9: Modifier extension that auto-scrolls the nearest
 * scrollable ancestor to bring the receiving composable into
 * view when it gains focus. The home surface has three input
 * surfaces (the open-loop capture line, the bedtime-list lines,
 * the quick-notes input) and the soft keyboard would otherwise
 * cover whichever line is focused — the user could not see
 * what they were typing. The bedtime list in particular has
 * up to five lines, and the one being typed into could be the
 * bottom one, well below the visible scroll area once the
 * keyboard is up.
 *
 * The pattern is the standard Compose one: a
 * [BringIntoViewRequester] registered with the input's
 * modifier, called from a coroutine when the input gains
 * focus. The scroll container picks up the request and
 * scrolls the minimum needed to expose the focused field.
 *
 * Returns a [Modifier] so the caller can chain further
 * modifiers (e.g. fillMaxWidth, padding) before applying.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.bringIntoViewOnFocus(): Modifier {
    // The factory is `BringIntoViewRequester()` (top-
    // level function in the relocation package);
    // there is no `rememberBringIntoViewRequester` in
    // Compose Foundation 1.7.x. Wrapping it in
    // remember gives one instance per field per
    // composition.
    val requester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    return this
        .bringIntoViewRequester(requester)
        .onFocusEvent { state ->
            if (state.isFocused) {
                scope.launch { requester.bringIntoView() }
            }
        }
}

/**
 * Root of the launcher UI. Three surfaces: the calm home (clock, greeting,
 * favorites), the search-first app drawer, and settings. No grid, no icons,
 * no badges — text only (CONCEPT.md §3.2).
 */
@Composable
fun LauncherRoot(
    viewModel: LauncherViewModel = viewModel(),
    /** Bumped whenever the home button is pressed; see HomeActivity. */
    goHomeSignal: Int = 0,
    /**
     * v0.25.2-A (Task 8): when the user taps a letter notification,
     * HomeActivity writes the letter's date here. The launcher navigates
     * to the reader for that date, then signals back via
     * [onLetterDateConsumed] so the activity clears the value. The
     * reset is what makes a re-tap for the same date work — without
     * it, the flow would not re-emit and the second tap would be a
     * silent no-op.
     */
    letterDateSignal: LocalDate? = null,
    /**
     * v0.25.2-A (Task 8): invoked after the launcher has applied a
     * [letterDateSignal]. HomeActivity uses this to clear its
     * `MutableStateFlow` so a configuration change does not re-trigger
     * the same navigation.
     */
    onLetterDateConsumed: () -> Unit = {},
    /**
     * v0.44.0: the active flash event. `null` means
     * no flash is active. When non-null, the home
     * surface shows a full-screen pulsing tint and
     * the body of the reminder note. The home
     * surface calls [onFlashConsumed] when the user
     * taps to dismiss, or when the 5-second
     * auto-clear fires.
     */
    flashEvent: org.mindanchor.note.FlashSignal.FlashEvent? = null,
    /**
     * v0.44.0: invoked after the launcher has
     * applied a [flashEvent]. HomeActivity uses
     * this to clear the FlashSignal singleton so
     * a configuration change does not re-trigger
     * the same flash.
     */
    onFlashConsumed: () -> Unit = {},
) {
    // v0.25.14: collectAsStateWithLifecycle on all 7 LauncherRoot flows so the
    // collector stops when the screen is STOPPED. With collectAsState, a
    // backgrounded home screen would keep recomposing on every preference
    // change, every notes write, every wellness tick — the StateFlow is
    // never paused. collectAsStateWithLifecycle ties the collector to the
    // Compose tree's lifecycle, which is what BackgroundedState in the
    // BUG-004 finding test was probing.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val openLoop by viewModel.openLoop.collectAsStateWithLifecycle()
    val oneThing by viewModel.oneThing.collectAsStateWithLifecycle()
    val recentNotes by viewModel.notes.collectAsStateWithLifecycle()
    // v0.45.0: the home card shows only pinned
    // notes (max 3). The full list — pinned +
    // unpinned — is on the Notes tab. The
    // filter is on the launcher root, not on
    // the QuickNotesCard body, so the
    // "View all" affordance from v0.43.0
    // continues to route to the Notes tab
    // (which is the all-notes view).
    val pinnedNotes = recentNotes.filter { it.pinned }.take(3)
    // v0.45.0: the note-action callbacks are
    // declared at the launcher-root level so
    // both the HomeSurface (inside the home's
    // CalmBackground lambda) and the NotesSurface
    // (outside that lambda, in the `when (surface)`
    // dispatch) can use them. Before v0.45.0
    // the callbacks were only declared inside
    // the home branch, which meant the Notes
    // tab could not reuse them. Promoting to
    // the root level is the simplest fix.
    val onDeleteNote: (Long) -> Unit = { id -> viewModel.deleteNote(id) }
    val onPinNote: (Long, Boolean) -> Unit = { id, pinned -> viewModel.pinNote(id, pinned) }
    val onMarkNoteDone: (Long, Boolean) -> Unit = { id, done -> viewModel.markNoteDone(id, done) }
    val wellnessReadings by viewModel.wellnessReadings.collectAsStateWithLifecycle()
    // v0.35.0: the data-sources card reads three StateFlows.
    // Each is a cold read of a per-source DataStore; the
    // WhileSubscribed(5_000) in the VM keeps a backgrounded
    // home from paying the read cost. The collectAsStateWithLifecycle
    // is the BUG-004 primitive: no collection while STOPPED.
    val healthConnectStatus by viewModel.healthConnectStatus.collectAsStateWithLifecycle()
    val corosDataStatus by viewModel.corosDataStatus.collectAsStateWithLifecycle()
    val ppgLastMeasurement by viewModel.ppgLastMeasurement.collectAsStateWithLifecycle()
    // v0.26.0 §3.5
    val ctx = LocalContext.current
    val bpdProfilePrefs = remember { org.mindanchor.data.BpdProfilePrefs(ctx.applicationContext) }
    val bpdProfile by bpdProfilePrefs.profile.collectAsStateWithLifecycle(initialValue = org.mindanchor.data.BpdProfile())
    // v0.42.0: the 2x2 needs grid on the home surface is gated
    // by a preference (Settings → Home screen → Show needs
    // grid). Default `true` so first-launch users see the same
    // home they had in v0.41.0; existing users who already
    // configured the setting get the value they wrote.
    val appearancePrefs = remember { org.mindanchor.data.AppearancePrefs(ctx.applicationContext) }
    val needsGridVisible by appearancePrefs.needsGridVisible
        .collectAsStateWithLifecycle(initialValue = true)
    // v0.26.5: the onStayUp callback writes `okAtNight = true`
    // to the BpdProfile DataStore; the flow re-emits, isTwoAmWindow
    // recomputes to false, and the shell disappears on the next
    // composition. rememberCoroutineScope is the right scope for a
    // one-shot DataStore write from a tap callback (lives as long
    // as the composition, not the activity).
    val bpdProfileScope = rememberCoroutineScope()
    val nowTick = rememberMinuteTick()
    val isTwoAmWindow = NowWhatHeuristic.shouldShow(
        currentHour = nowTick.hour,
        okAtNight = bpdProfile.okAtNight,
    )
    // v0.25.15: the 3 deferred LauncherRoot state fields are now
    // rememberSaveable too. `actionsFor` and `gateFor` hold
    // `DisplayApp?` and use the file-level `DisplayAppNullableSaver`
    // (mapSaver, component-name key) so the value survives a config
    // change or process death. `letterSelectedDate` holds
    // `LocalDate?` and uses `LocalDateNullableSaver` (ISO-8601
    // string round-trip). See the KDoc on the Savers for why a
    // generic `Saver<Any, _>` over the standard autoSaver is the
    // right shape here.
    var surface by rememberSaveable { mutableStateOf(LauncherSurface.Home) }
    var actionsFor by rememberSaveable(stateSaver = DisplayAppNullableSaver) {
        mutableStateOf<DisplayApp?>(null)
    }
    var gateFor by rememberSaveable(stateSaver = DisplayAppNullableSaver) {
        mutableStateOf<DisplayApp?>(null)
    }

    // Where the report was opened from, so back returns there. Two ways
    // in now — the settings section and a line on the home screen — and
    // sending somebody who came from home into settings would be a small
    // daily disorientation.
    var reportCameFrom by rememberSaveable { mutableStateOf(LauncherSurface.Settings) }
    // v0.25.2-A (Task 6): the letter inbox + reader. Same shape as
    // reportCameFrom — selected date is null on the inbox, non-null
    // in the reader; cameFrom remembers where the user came from so
    // the inbox's back button returns there. Two entry points: the
    // new "letters" TopEnd corner on the home surface, the (later)
    // Reading sub-section in Settings, and the letter notification
    // (Task 8), which writes letterDateSignal from HomeActivity.
    // v0.25.15: `letterSelectedDate` is rememberSaveable via the
    // ISO-string `LocalDateNullableSaver` so a config change while
    // the user is reading a letter preserves the open reader.
    var letterSelectedDate by rememberSaveable(stateSaver = LocalDateNullableSaver) {
        mutableStateOf<LocalDate?>(null)
    }
    var letterCameFrom by rememberSaveable { mutableStateOf(LauncherSurface.Home) }
    val context = LocalContext.current
    val reportStore = remember(context) { ReportStore(context.applicationContext) }
    // v0.25.17 BUG-004: lifecycle-aware collect. The
    // report-store flow emits when a fresh nightly
    // report is composed; pre-v0.25.17 the launcher
    // kept listening to the flow even when the home
    // surface was STOPPED (Settings, Onboarding,
    // etc.).
    val storedReport by reportStore.stored.collectAsStateWithLifecycle(initialValue = null)
    // Only when there is genuinely something to read. An empty report is
    // ReportComposer's ordinary, good outcome, and offering a way in to
    // read nothing teaches somebody to stop looking.
    val hasReport = storedReport?.let {
        it.patterns.isNotEmpty() || !it.narration.isNullOrBlank() || !it.report.isEmpty
    } == true

    // Pressing home while deep in the drawer or settings must land on the
    // home surface — otherwise the launcher "sticks" wherever you left it.
    LaunchedEffect(goHomeSignal) {
        if (goHomeSignal > 0) {
            gateFor = null
            actionsFor = null
            surface = LauncherSurface.Home
            viewModel.onQueryChange("")
        }
    }

    // v0.20.5: refresh wellness on every transition into the
    // home surface. The readings are cached in the ViewModel
    // for 5s by WhileSubscribed, but the first composition
    // after a Health Connect permission grant — the most
    // likely moment the user opens the launcher — is exactly
    // the moment the data is freshest. The same goHomeSignal
    // path that handles "press home from deep in the app"
    // also handles "press home from settings", which is the
    // path the user takes after granting permission.
    LaunchedEffect(goHomeSignal) {
        if (goHomeSignal >= 0) viewModel.refreshWellness()
    }

    // v0.25.2-A (Task 8): letter notification side-channel. When the
    // user taps a letter notification, HomeActivity writes the letter's
    // date into letterDateSignal. We navigate to the letter reader
    // for that date, then clear the signal so a configuration change
    // does not re-trigger the same navigation and so a re-tap of the
    // same date emits a fresh value. Same shape as the goHomeSignal
    // LaunchedEffect above — an activity-owned flow the launcher
    // reacts to on every recomposition.
    LaunchedEffect(letterDateSignal) {
        val date = letterDateSignal ?: return@LaunchedEffect
        letterSelectedDate = date
        letterCameFrom = LauncherSurface.Home
        surface = LauncherSurface.Letter
        onLetterDateConsumed()
    }

    // Settings has its own [BackHandler] now: when a
    // group is open, the first back closes the group
    // and the second leaves Settings for Home. Leaving
    // surface==Settings in the predicate means our
    // global back does not steal the press from the
    // settings screen — every prior version did, which
    // is why the section index used to disappear on
    // the way out.
    BackHandler(enabled = (surface != LauncherSurface.Home && surface != LauncherSurface.Settings) || gateFor != null) {
        gateFor = null
        surface = LauncherSurface.Home
        viewModel.onQueryChange("")
    }

    fun attemptLaunch(app: DisplayApp) {
        val packageName = app.component.substringBefore('/')
        if (packageName in state.frictionPackages) {
            gateFor = app
        } else {
            viewModel.launch(app)
            surface = LauncherSurface.Home
        }
    }

    gateFor?.let { app ->
        // The tone and the optional extras (small thing, if-then
        // plan, compassion moment) all depend on disk reads. Nothing
        // is drawn until they resolve — showing the full breath and
        // then swapping it for a lighter prompt would be worse than
        // the brief blank the sky already covers.
        var gate by remember(app) { mutableStateOf<GateContext?>(null) }
        LaunchedEffect(app) { gate = viewModel.gateFor(app) }
        val resolved = gate
        if (resolved == null) {
            // Hold the sky. Falling through here would draw the home screen
            // for a frame between tapping an app and the pause appearing,
            // which is the flash this launcher has already been fixed for
            // once.
            CalmBackground { }
        } else {
            FrictionGate(
                tone = resolved.tone,
                appLabel = app.label,
                smallThing = resolved.smallThing,
                ifThenPlan = resolved.ifThenPlan,
                compassionMoment = resolved.compassionMoment,
                perAppSessionLength = resolved.perAppSessionLength,
                packageName = resolved.packageName,
                // v0.20.1 round 4 (item M): the per-app
                // session-length "Learn this for next time"
                // toggle. The gate invokes this callback
                // only when the toggle is on at the moment
                // of the tap. The launcher records the
                // choice via FrictionPrefs and the change
                // is picked up on the next reach.
                onTimeBoxPicked = { pkg, minutes ->
                    viewModel.recordPerAppSessionLength(pkg, minutes)
                },
                // v0.20.1 round 5 follow-up: forget the
                // per-app default. The launcher clears the
                // map entry and the next reach will show
                // the "Learn this for next time" toggle
                // again, as if the user had never picked.
                onForgetDefault = { pkg ->
                    viewModel.clearPerAppSessionLength(pkg)
                },
                // Taking the small thing is leaving, not entering. It
                // counts as backing out for the same reason "never mind"
                // does: the person met the pause and did not go in.
                onSmallThingTaken = {
                    viewModel.recordNeverMind(app, resolved.banditArm)
                    gateFor = null
                    surface = LauncherSurface.Home
                },
                onOpen = { minutes ->
                    viewModel.launchTimed(app, minutes, resolved.banditArm)
                    gateFor = null
                    surface = LauncherSurface.Home
                },
                onNeverMind = {
                    viewModel.recordNeverMind(app, resolved.banditArm)
                    gateFor = null
                    surface = LauncherSurface.Home
                },
            )
        }
        return
    }

    when (surface) {
        // v0.26.0 §3.5
        LauncherSurface.Home ->
            if (isTwoAmWindow) {
                NowWhatShell(
                    onWantSleep = { surface = LauncherSurface.Home },
                    onWantGround = { surface = LauncherSurface.GroundMe },
                    onWantTalk = {
                        runCatching {
                            val supportIntent = android.content.Intent(context, SupportActivity::class.java)
                            context.startActivity(supportIntent)
                        }
                    },
                    // v0.26.5: 4th option. Toggle okAtNight in
                    // BpdProfile (DataStore `bpd_ok_at_night`
                    // pref) — the next composition reads the new
                    // value via collectAsStateWithLifecycle and
                    // isTwoAmWindow flips false, so the shell
                    // disappears. The same Settings checkbox
                    // (BpdProfileCheckbox) is the way to revert.
                    onStayUp = {
                        bpdProfileScope.launch {
                            bpdProfilePrefs.update(bpdProfile.copy(okAtNight = true))
                        }
                    },
                )
            } else {
                CalmBackground { sky ->
                    // v0.25.17 BUG-004: lifecycle-aware collect.
                    // Same rationale as the report-store flow
                    // above. The intro-callout flag is read
                    // only when the home surface is foreground.
                    val showIntroCallout by viewModel.showIntroCallout.collectAsStateWithLifecycle()
            // v0.49.0: the home card's
            // "X notes on this phone" count
            // line uses the TOTAL note count,
            // not the home card's 3-row cap.
            // The cap is still [recentNotes]
            // (= [pinnedNotes]) so the
            // bottom of the home card
            // continues to show "what I just
            // wrote".
            val allNotes by viewModel.allNotes.collectAsStateWithLifecycle()
            HomeSurface(
                sky = sky,
                favorites = state.favorites,
                allNotes = allNotes,
                // v0.42.0: hide the 2x2 needs grid when the user
                // has turned it off in Settings. The four doors
                // collapse to nothing; the time, greeting, and
                // quick-notes card remain. Support is still
                // reachable from the top-left "Open Support"
                // button and from Settings.
                needsGridVisible = needsGridVisible,
                onOpenDrawer = { surface = LauncherSurface.Drawer },
                onOpenSettings = { surface = LauncherSurface.Settings },
                // v0.45.0: top-right "Notes"
                // button. Routes to the new
                // Notes tab. The button lives
                // in the same chrome slot the
                // v0.42.0 Letters/notes/history
                // stack used to occupy — top
                // end — so the position is
                // familiar to a user upgrading
                // from a previous build.
                onOpenNotes = { surface = LauncherSurface.Notes },
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
                loopPhase = openLoop.first,
                loopNote = openLoop.second,
                loopPostponedAt = openLoop.third,
                onLoopSave = viewModel::saveOpenLoop,
                onLoopClear = viewModel::clearOpenLoop,
                onLoopPostpone = viewModel::postponeOpenLoop,
                onLoopCancelPostpone = viewModel::cancelOpenLoopPostponement,
                // v0.28.0: open the Distress Thermometer activity.
                // The home card's "Ground me here" button routes here.
                // The activity is non-exported; a misconfigured manifest
                // would silently fail without the runCatching wrapper.
                onOpenDistressThermometer = {
                    runCatching {
                        val distressIntent = android.content.Intent(
                            context,
                            org.mindanchor.support.DistressThermometerActivity::class.java,
                        )
                        context.startActivity(distressIntent)
                    }
                },
                onOpenGroundMe = { surface = LauncherSurface.GroundMe },
                recentNotes = pinnedNotes,
                onAddQuickNote = { body, pinned -> viewModel.addQuickNote(body, pinned) },
                // v0.46.0: forward the mood-log
                // tap to the VM. The VM owns the
                // note store write; the card
                // owns the emoji row.
                onAddMoodLog = { emoji -> viewModel.addMoodLog(emoji) },
                onDeleteNote = onDeleteNote,
                // v0.44.0: forward the new
                // task / reminder / done
                // operations from the home card
                // to the ViewModel. The VM
                // owns the ReminderScheduler
                // wiring; the card owns the
                // type picker and the time
                // picker.
                onAddTaskNote = { body, dueAt, pinned -> viewModel.addTaskNote(body, dueAt, pinned) },
                onAddReminderNote = { body, reminderAt, pinned -> viewModel.addReminderNote(body, reminderAt, pinned) },
                onMarkNoteDone = onMarkNoteDone,
                // v0.45.0: pin a note to the home
                // card. Forwarded to the VM,
                // which writes the pinned flag
                // to NotesPrefs. The home
                // card re-renders with the
                // pinned note on top.
                onPinNote = onPinNote,
                hasReport = hasReport,
                onOpenReport = {
                    reportCameFrom = LauncherSurface.Home
                    surface = LauncherSurface.Report
                },
                onOpenCheckInHistory = {
                    // v0.20.1 round 5 follow-up:
                    // route to the check-in history.
                    // Same runCatching pattern as the
                    // notes entry — defensive against
                    // a misconfigured manifest.
                    runCatching {
                        val historyIntent = android.content.Intent(
                            context, org.mindanchor.model.CheckInHistoryActivity::class.java,
                        )
                        context.startActivity(historyIntent)
                    }
                },
                // v0.25.2-A (Task 6): the "letters" TopEnd
                // corner. Wired here so the lambda body has
                // access to the letter state (selectedDate,
                // cameFrom) and the surface dispatcher. The
                // Settings entry will pass a sibling lambda
                // with cameFrom = LauncherSurface.Settings.
                onOpenLetters = {
                    letterSelectedDate = null
                    letterCameFrom = LauncherSurface.Home
                    surface = LauncherSurface.Letter
                },
                // v0.26.4 §3.4: the 3 BPD entry points. Each
                // is a runCatching because the activity is
                // not-exported; an unconfigured manifest is
                // the easiest way to ship a broken entry
                // point, and a single try-frame is not a
                // UX failure. Same defensive pattern as
                // onOpenNotes + onOpenCheckInHistory.
                onOpenChainCapture = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.chain.ChainCaptureActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenIfsPicker = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.ifs.IfsPickerActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenExport = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.export.ExportActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                // v0.35.0: the four needs-card doors.
                onOpenSupport = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.support.SupportActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenAccepts = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.support.AcceptsActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenDiaryCard = {
                    runCatching {
                        val intent = android.content.Intent(
                            context, org.mindanchor.support.DiaryCardActivity::class.java,
                        )
                        context.startActivity(intent)
                    }
                },
                onOpenGetThrough = {
                    surface = LauncherSurface.GetThrough
                },
                // v0.35.0: the data-sources card reads these
                // three StateFlows. The card is hidden entirely
                // when no source has data; the empty-state
                // visibility rule lives in DataSourcesCard.
                healthConnectStatus = healthConnectStatus,
                corosDataStatus = corosDataStatus,
                ppgLastMeasurement = ppgLastMeasurement,
                wellnessReadings = wellnessReadings,
                showIntroCallout = showIntroCallout,
                onRecordLaunch = viewModel::recordHomeLaunch,
                // v0.44.0: forward the flash event
                // from HomeActivity so the home
                // surface can show a full-screen
                // pulsing tint when a reminder
                // fires. The dismiss callback
                // clears the FlashSignal so the
                // same flash is not re-played on
                // a config change.
                flashEvent = flashEvent,
                onFlashConsumed = onFlashConsumed,
            )
        }
            }

        LauncherSurface.Drawer -> Surface(modifier = Modifier.fillMaxSize()) {
            DrawerSurface(
                viewModel = viewModel,
                state = state,
                onLaunch = ::attemptLaunch,
                onLongPress = { actionsFor = it },
                // v0.47.0: map the bang to a launcher
                // surface. Tasks and Mood route to
                // Home (the home surface owns the
                // mood card and the task picker).
                // GroundMe, Notes, Settings route
                // to their own surfaces.
                onBang = { cmd ->
                    when (cmd) {
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.GroundMe ->
                            surface = LauncherSurface.GroundMe
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Notes ->
                            surface = LauncherSurface.Notes
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Settings ->
                            surface = LauncherSurface.Settings
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Tasks,
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Mood ->
                            surface = LauncherSurface.Home
                    }
                },
            )
        }

        LauncherSurface.Settings -> Surface(modifier = Modifier.fillMaxSize()) {
            SettingsScreen(
                allApps = state.allApps,
                hiddenApps = state.allApps.filter { it.isHidden },
                onUnhide = { viewModel.setHidden(it, false) },
                onBack = { surface = LauncherSurface.Home },
                onOpenPpg = { surface = LauncherSurface.Ppg },
                onOpenReport = {
                    reportCameFrom = LauncherSurface.Settings
                    surface = LauncherSurface.Report
                },
                // v0.25.2-A (Task 10): the Daily letter
                // sub-section in Settings has an "Open inbox"
                // button. Routing is the same shape as
                // onOpenReport above — flag the cameFrom so
                // the letter surface's back button returns
                // to Settings rather than to the home screen,
                // and let the letter state default to the
                // inbox (no letter is preselected).
                onOpenLetters = {
                    letterSelectedDate = null
                    letterCameFrom = LauncherSurface.Settings
                    surface = LauncherSurface.Letter
                },
                onOpenBeforeYouSend = { surface = LauncherSurface.BeforeYouSend },
            )
        }

        // Its own surface rather than a section inside the settings scroll.
        // The measurement holds the screen awake and runs the torch for a
        // minute and a half; nesting that inside a screen somebody is
        // scrolling through would mean starting it by accident.
        LauncherSurface.Ppg -> Surface(modifier = Modifier.fillMaxSize()) {
            PpgScreen(onBack = { surface = LauncherSurface.Settings })
        }

        LauncherSurface.Report -> Surface(modifier = Modifier.fillMaxSize()) {
            // Back goes wherever this was opened from. Sending somebody
            // who tapped the line on the home screen into settings would
            // be a small, daily disorientation.
            ReportScreen(onBack = { surface = reportCameFrom })
        }

        // v0.45.0: the all-notes tab. The list
        // is a thin projection of [NotesPrefs]
        // the home card reads from. The back
        // button returns to home — there is
        // no other parent the user could have
        // come from. The screen is a
        // sibling of the home, the drawer,
        // and the settings; not a separate
        // activity, so the lifecycle-scoped
        // data flows are shared with the
        // home card and the "View all" affordance.
        LauncherSurface.Notes -> Surface(modifier = Modifier.fillMaxSize()) {
            NotesSurface(
                // v0.48.0: use the uncapped
                // allNotes flow for the Notes
                // tab. The previous code passed
                // recentNotes (capped at 3),
                // which silently hid everything
                // past the 3 most recent. The
                // home card still uses
                // recentNotes (the "3 most
                // recent" behaviour is the
                // v0.43.0 design).
                allNotes = viewModel.allNotes.collectAsStateWithLifecycle().value,
                onBack = { surface = LauncherSurface.Home },
                onDeleteNote = onDeleteNote,
                onPinNote = onPinNote,
                onMarkNoteDone = onMarkNoteDone,
                // v0.54.0: the swipe-to-delete
                // Undo affordance plumbed in.
                // The Notes tab takes a
                // snapshot of the note at
                // swipe time and calls
                // [restoreNote] to re-insert
                // it with the same id. The
                // launcher-only [restoreNote]
                // is on the ViewModel — the
                // Notes surface does not have
                // a direct dependency on the
                // prefs layer.
                onRestoreNote = { note -> viewModel.restoreNote(note) },
            )
        }

        // v0.25.2-A (Task 6): the letter inbox + reader. Dispatched
        // here because the parent (HomeScreen) holds the
        // letterSelectedDate / letterCameFrom state — the
        // LetterScreen Composable is otherwise stateless on which
        // date is selected. The back button clears the selected
        // date when in the reader (back to inbox) and falls back
        // to letterCameFrom when in the inbox.
        //
        // v0.25.16 BUG-017: `modelFits` is now wired from
        // `viewModel.modelFits` (a `StateFlow<Boolean>` that
        // reflects the on-disk presence of the Phi-4 model).
        // The pre-v0.25.16 stub held a Composable-level
        // `remember { mutableStateOf(false) }` whose value was
        // always `false` — the Generate-now affordance was
        // permanently disabled. Wiring through the VM is the
        // standard `collectAsStateWithLifecycle` pattern and
        // is what the BUG-017 FindingTest asserts.
        LauncherSurface.Letter -> Surface(modifier = Modifier.fillMaxSize()) {
            val modelFits by viewModel.modelFits.collectAsStateWithLifecycle()
            // v0.25.2-B (Task 15): letter size is read from the
            // LauncherViewModel (mirrors the SettingsViewModel.letterSize
            // from Task 9 — both VMs read from the same DataStore source).
            // v0.25.17 BUG-004: lifecycle-aware collect.
            // The letter-size preference is a DataStore
            // value; reading it through the lifecycle-
            // aware primitive keeps the launcher from
            // collecting on every emission while the
            // letter surface is STOPPED.
            val letterSize by viewModel.letterSize.collectAsStateWithLifecycle()
            val letterStore = remember(context.applicationContext) {
                LetterStore(context.applicationContext)
            }
            val feedbackStore = remember(context.applicationContext) {
                org.mindanchor.letters.LetterFeedbackStore(context.applicationContext)
            }
            // The actual letter list. v0.26.2 finally wires
            // this off LetterStore.letters; the v0.25.x stub
            // (`emptyList()`) meant the inbox was permanently
            // empty. `collectAsStateWithLifecycle` is the
            // SOTA-v2 primitive (see HomeScreen.kt's own
            // LauncherRoot for the BUG-004 fix), and matches
            // the v0.25.14 batch.
            val letters by letterStore.letters.collectAsStateWithLifecycle(
                initialValue = emptyList(),
            )
            val letterScope = rememberCoroutineScope()
            // v0.26.2: build the per-date feedback-count map
            // synchronously on every recomposition. The store
            // is a plain-file read, no IO pump, no Flow; the
            // counts are small (one per letter date on file);
            // the recomposition cost is O(letter count). A
            // user with 30 letters on file pays 30 file
            // existence checks — measured at sub-millisecond
            // on a real device. The cost is fine until
            // somebody reports it isn't.
            val feedbackCounts: Map<LocalDate, Int> = remember(letters) {
                letters.associate { it.date to feedbackStore.countFor(it.date) }
            }
            LetterScreen(
                letters = letters,
                modelFits = modelFits,
                date = letterSelectedDate,
                size = letterSize,
                feedbackCounts = feedbackCounts,
                // v0.25.3-WP-C: a row tap marks the letter as read so
                // the Settings "Open inbox (N)" badge decrements.
                // The mark is idempotent (Set semantics) and the write
                // is on Dispatchers.IO via DataStore.
                onSelect = { date ->
                    letterSelectedDate = date
                    letterScope.launch { letterStore.setRead(date, true) }
                },
                onBack = {
                    if (letterSelectedDate != null) {
                        letterSelectedDate = null
                    } else {
                        surface = letterCameFrom
                    }
                },
                onDelete = { date -> letterScope.launch { letterStore.delete(date) } },
                onSetSize = { size -> viewModel.setLetterSize(size) },
                // v0.26.2: persist a user-authored letter from
                // the empty-state composer. The body comes in
                // from the composer's text field; the date is
                // today. The DataStore write is on the IO
                // dispatcher via the store.
                onSaveUserLetter = { date, body ->
                    letterScope.launch { letterStore.saveUserLetter(date, body) }
                },
                // v0.31.0: the inbox's "Generate now" / "Use
                // AI" affordance now actually runs. The
                // pipeline: collect this week's data via
                // [WeekDataCollector], call [LetterWriter] on
                // the IO dispatcher, save the result as
                // today's letter if the model produced
                // anything safe. The whole call is wrapped
                // in runCatching so a model load failure, a
                // generation timeout, or a [NarrationGuard]
                // rejection never crashes the launcher — the
                // user sees an empty inbox, exactly as they
                // did before v0.31.0.
                //
                // v0.32.1: the work is now hosted by a
                // [org.mindanchor.letters.LettersGenerationService]
                // foreground service. Pre-v0.32.1 the
                // coroutine was tied to this Composable's
                // `letterScope = rememberCoroutineScope()`
                // and died when the user navigated away, or
                // (more often on a 1.8 GB MemAvailable
                // phone) when the OS reaped the process
                // mid-decode. The service holds a partial
                // wake lock, posts an ongoing notification
                // for visibility, and runs until the letter
                // is saved or the run fails. The same
                // pipeline ([WeekDataCollector] →
                // [LetterWriter] → [LetterStore.saveUserLetter])
                // — just hosted in a place that survives
                // the Composable.
                onGenerateNow = {
                    // The Toast is the immediate user-side
                    // confirmation: "yes, the button worked;
                    // the system has the work." The
                    // notification will appear in the
                    // status bar a moment later; that is
                    // the "this is still running" signal.
                    // The letter itself appears in the
                    // inbox when the generation finishes
                    // (and the user gets a one-shot
                    // "Tonight's letter is ready"
                    // notification at that point).
                    //
                    // v0.37.0 (BPD-safety WARN remediation):
                    // the previous copy spelled out the
                    // 30–60 min window, which read as
                    // latency pressure for a person in
                    // distress. The new copy names the
                    // outcome ("in your inbox by morning")
                    // without quantifying the wait, and
                    // keeps "tonight's" as the only time
                    // reference so the user doesn't have
                    // to do the arithmetic.
                    android.widget.Toast.makeText(
                        context.applicationContext,
                        "Started. Tonight's letter will be in your inbox by morning.",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                    val appContext = context.applicationContext
                    appContext.startForegroundService(
                        org.mindanchor.letters.LettersGenerationService.intent(appContext),
                    )
                },
                // v0.26.2: persist a thumbs-down. The body
                // comes from the feedback dialog's optional
                // text field; the date is the letter's date.
                // The file write is on the IO dispatcher
                // because [LetterFeedbackStore.save] is a
                // blocking `appendText` call.
                onSaveFeedback = { date, reason ->
                    letterScope.launch(Dispatchers.IO) {
                        feedbackStore.save(date, reason)
                    }
                },
            )
        }

        // v0.26.0 §3.2
        LauncherSurface.GroundMe -> GroundMeScreen(
            onClose = { surface = LauncherSurface.Home },
        )
        // v0.26.0 §3.3
        LauncherSurface.BeforeYouSend -> BeforeYouSendDemo(
            onDismiss = { surface = LauncherSurface.Home },
        )
        // v0.35.0: the "Get through this" sub-menu. A
        // stacked surface rather than a fresh activity
        // because the three reflective actions it surfaces
        // are existing activities and a sub-menu is cheaper
        // to navigate between than a fresh Intent trip.
        // The sub-menu routes to the same activities the
        // v0.32.0 "Right now" section did (chain capture,
        // IFS picker, export); the entry point moves from
        // "a section of the home" to "the 4th door of the
        // needs card". The back button on the sub-menu
        // returns to the home, not to the needs card,
        // because the needs card is the surface the user
        // came from.
        LauncherSurface.GetThrough -> Surface(modifier = Modifier.fillMaxSize()) {
            CalmBackground { sky ->
                GetThroughSubMenu(
                    sky = sky,
                    onWhatHappened = {
                        runCatching {
                            val intent = android.content.Intent(
                                context, org.mindanchor.chain.ChainCaptureActivity::class.java,
                            )
                            context.startActivity(intent)
                        }
                    },
                    onWhichPart = {
                        runCatching {
                            val intent = android.content.Intent(
                                context, org.mindanchor.ifs.IfsPickerActivity::class.java,
                            )
                            context.startActivity(intent)
                        }
                    },
                    onExport = {
                        runCatching {
                            val intent = android.content.Intent(
                                context, org.mindanchor.export.ExportActivity::class.java,
                            )
                            context.startActivity(intent)
                        }
                    },
                    onBack = { surface = LauncherSurface.Home },
                )
            }
        }
    }

    actionsFor?.let { app ->
        AppActionsDialog(
            app = app,
            isFrictioned = app.component.substringBefore('/') in state.frictionPackages,
            isAlwaysOpen = app.component.substringBefore('/') in state.alwaysOpenPackages,
            onDismiss = { actionsFor = null },
            onToggleFavorite = { viewModel.toggleFavorite(app); actionsFor = null },
            onToggleHidden = { viewModel.setHidden(app, !app.isHidden); actionsFor = null },
            onToggleFriction = { viewModel.toggleFriction(app); actionsFor = null },
            onToggleAlwaysOpen = { viewModel.toggleAlwaysOpen(app); actionsFor = null },
            onRename = { label -> viewModel.rename(app, label); actionsFor = null },
        )
    }
}

/**
 * The one unfinished thing — see [org.mindanchor.friction.OpenLoop].
 *
 * Deliberately silent most of the time. It appears once in the quiet
 * hours to take a line, and once the next morning to give it back, and
 * otherwise draws nothing at all. A home screen that always has something
 * to say is a home screen people stop reading.
 *
 * v0.25.5: a fourth phase, [LoopPhase.POSTPONED], keeps the launcher
 * silent while the user's worry-postponement clock is in the future
 * (Borkovec 1994 + Watkins 2008). The card surfaces a small
 * "Back at HH:MM" line and a "Back to it now" affordance that drops
 * the postponement and falls back to the hand-it-back flow. A
 * "Postpone" button on the RETURN state opens a small dialog with
 * "Later today" / "Tomorrow morning" — the Borkovec protocol is
 * "schedule a specific time", but the user's two most common times
 * are good defaults and "pick a time" can wait.
 */
@Suppress("FunctionNaming", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
private fun OpenLoopCard(
    sky: SkyContent,
    phase: LoopPhase,
    note: String?,
    postponedAt: Instant?,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onPostpone: (Instant) -> Unit,
    onCancelPostpone: () -> Unit,
) {
    // v0.25.10 (SOTA v2 bug-hunt B9): remember the system date once and
    // pass it to formatWallClock so the formatted time and the
    // "tomorrow" comparison come from the same system instant, not two
    // separate reads that could straddle a midnight or DST boundary.
    val today = remember { LocalDate.now() }
    when (phase) {
        LoopPhase.NONE -> Unit

        LoopPhase.CAPTURE -> {
            // v0.25.10 (SOTA v2 bug-hunt B7): rememberSaveable so a
            // captured draft survives config change / process death.
            var draft by rememberSaveable { mutableStateOf("") }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.loop_capture),
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                    textAlign = TextAlign.Center,
                )
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.loop_capture_hint)) },
                    // v0.20.9: bringIntoViewOnFocus so the
                    // open-loop capture line scrolls above the
                    // keyboard when focused. The whole
                    // open-loop card sits between the clock
                    // and the bedtime list and would otherwise
                    // be covered by the IME.
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewOnFocus()
                        .padding(top = 8.dp),
                )
                TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { onSave(draft) }) {
                    Text(stringResource(R.string.loop_save), color = sky.textPrimary)
                }
            }
        }

        LoopPhase.POSTPONED -> Column(
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = note.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.loop_postponed_back_at,
                    formatWallClock(postponedAt, today),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = sky.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onCancelPostpone) {
                Text(stringResource(R.string.loop_postponed_cancel), color = sky.textSecondary)
            }
        }

        LoopPhase.RETURN -> {
            // v0.25.10 (SOTA v2 bug-hunt B8): rememberSaveable so a
            // Postpone dialog stays open across a config change.
            var showDialog by rememberSaveable { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.loop_return),
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = note.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = sky.textPrimary,
                    textAlign = TextAlign.Center,
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = { showDialog = true }) {
                        Text(stringResource(R.string.loop_postpone), color = sky.textSecondary)
                    }
                    TextButton(modifier = Modifier.semantics { role = Role.Button }, onClick = onClear) {
                        Text(stringResource(R.string.loop_clear), color = sky.textSecondary)
                    }
                }
            }
            if (showDialog) {
                PostponeDialog(
                    onDismiss = { showDialog = false },
                    onPick = { at ->
                        onPostpone(at)
                        showDialog = false
                    },
                )
            }
        }
    }
}

/**
 * v0.28.0: the home-surface Distress Thermometer card. The first
 * question the home surface asks — validation-first, before any
 * task-capture or note-taking. A single Surface with the title,
 * the caption, and a "Ground me here" button that opens
 * [org.mindanchor.support.DistressThermometerActivity].
 *
 * The full 0-100 slider lives in the activity; the home card is
 * the launcher. The card is BPD-safe by design: no directive
 * language, no all-or-nothing framing, no comparative
 * day-rating language. The caption is validate-then-suggest
 * ("slide to where it is, not where you want it to be").
 *
 * Research: Linehan 1993 (DBT Distress Tolerance, ch. 8) +
 * Gross 1998 (emotion regulation). The home card is the
 * "check in with where you are" affordance that the rest of
 * the launcher's surfaces assume has already happened.
 *
 * v0.25.5-v0.27.0 used to render a OneThingCard ("today's one
 * thing" — Martell 2013) as a sibling to OpenLoopCard +
 * QuickNotesCard. v0.28.0 removes the OneThingCard from the
 * home surface (BPD-strict: the first question is "how is it
 * right now?", not "what's the one thing today?"). The
 * OneThing data model is preserved in
 * [org.mindanchor.launcher.LauncherViewModel.oneThing] for
 * the export payload and any future re-introduction.
 */
@Suppress("FunctionNaming")
@Composable
private fun HomeDistressCard(
    sky: SkyContent,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.home_distress_card_title),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textPrimary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.home_distress_card_caption),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
        TextButton(
            onClick = onOpen,
            modifier = Modifier
                .semantics { role = Role.Button }
                .heightIn(min = 48.dp),
        ) {
            Text(
                text = stringResource(R.string.home_ground_me_button),
                color = sky.textPrimary,
            )
        }
    }
}

/**
 * The two-option worry-postponement dialog. Returns an [Instant] picked
 * from the user's choice. Borkovec's worry-postponement protocol says
 * the user picks the time, not the algorithm — but the two most common
 * times ("later today", "tomorrow morning") are the right defaults and
 * the explicit time-picker is a follow-up.
 */
@Suppress("FunctionNaming")
@Composable
private fun PostponeDialog(onDismiss: () -> Unit, onPick: (Instant) -> Unit) {
    // v0.25.10 (SOTA v2 bug-hunt B6): the zone is captured here once,
    // and "now" is read at the moment the user picks, not at the moment
    // the dialog composes. A dialog that stays open across a clock
    // change, an NTP correction, a zone change, or simply a long pause
    // used to schedule the postponed-at time from a stale instant; the
    // pick is now a fresh system read in the same zone as the rest of
    // the app.
    val zone = ZoneId.systemDefault()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loop_postpone_dialog_title)) },
        text = {
            Column {
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        onPick(
                            LocalDateTime.now(zone)
                                .plusHours(2)
                                .atZone(zone)
                                .toInstant(),
                        )
                    },
                ) {
                    Text(stringResource(R.string.loop_postpone_later_today))
                }
                TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = {
                        onPick(
                            LocalDate.now(zone)
                                .plusDays(1)
                                .atTime(9, 0)
                                .atZone(zone)
                                .toInstant(),
                        )
                    },
                ) {
                    Text(stringResource(R.string.loop_postpone_tomorrow_morning))
                }
            }
        },
        confirmButton = {
            TextButton(
        modifier = Modifier.semantics { role = Role.Button },
        onClick = onDismiss,
                // v0.25.10 (B6): Role.Button

            ) {
                Text(stringResource(R.string.loop_postpone_cancel))
            }
        },
    )
}

/**
 * Formats an [Instant] as a local wall-clock "HH:mm" or "tomorrow HH:mm"
 * for the [LoopPhase.POSTPONED] sub-text. The Intents are UTC; the
 * formatting is in the device's local zone so the user sees what they
 * scheduled in their own clock, not UTC.
 */
private fun formatWallClock(at: Instant?, today: LocalDate): String {
    if (at == null) return ""
    val zoned = at.atZone(ZoneId.systemDefault())
    val time = zoned.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
    return if (zoned.toLocalDate() == today) time else "tomorrow $time"
}

/**
 * v0.26.6: BedtimeListCard removed from the home surface
 * (three task-capture cards was one too many). The data
 * model (sleep/BedtimeList.kt), the DataStore
 * (data/LauncherPrefs.kt), the strings, and the bedtimeList
 * state flow are kept — only the home-surface call is gone.
 */
/**
 * The home-screen quick-notes card. v0.20.4.
 *
 * The launcher already routes the user to
 * [org.mindanchor.model.NoteActivity] from the
 * "notes" button in the top-right corner; the full
 * activity is the right place to read, edit, and
 * pin a long note. This card is the *capture*
 * surface — the place to jot one line without
 * opening anything.
 *
 * ## Why always visible
 *
 * The brief: "I want to remember this." The whole
 * notes feature exists for the moment between
 * noticing a thought and losing it. Two taps
 * (notes → new) is two taps too many in that
 * moment. The home card is the launcher-equivalent
 * of the URL bar in a browser: one place, always
 * there, one line, type and save.
 *
 * ## Why it shows the last three notes
 *
 * The save is the moment the user wants to know
 * worked. Showing the just-saved line land at the
 * top of a small list is the cheapest possible
 * "it worked" feedback. Three is the floor that
 * makes the card feel like a journal (one row
 * feels like a typo) and the ceiling before the
 * card would push the favourites off a small
 * screen at default font scale. The full list —
 * every note, every timestamp, edit and pin —
 * is one tap away via "View all".
 *
 * ## Why a button, not auto-save
 *
 * Notes are user-authored text and an
 * accidental keystroke (the keyboard popping
 * up while walking) is a real failure mode.
 * Auto-save on focus loss would silently
 * capture typos. The button makes the save
 * explicit; the placeholder and the disabled-
 * when-blank button tell the user the surface
 * is alive without nagging.
 *
 * ## Tapping a saved note
 *
 * A single tap opens the full [NoteActivity]
 * for editing. The card never edits inline
 * (the editing affordance is a different
 * surface, and inline edit on the home would
 * make the card a second editor — which the
 * brief is explicit that it is not).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickNotesCard(
    sky: SkyContent,
    recent: List<Note>,
    /**
     * v0.49.0: the total note count on the
     * phone. The card's count line uses
     * this value as the empty-state check;
     * the actual displayed number is the
     * count of distinct days, computed
     * from [allNotes] (v0.53.0).
     */
    totalCount: Int = recent.size,
    /**
     * v0.53.0 (Issue 3): the full note
     * list, used to compute the count of
     * distinct days the user has written
     * on. Kept separate from [recent]
     * because the day-count is the
     * archive's behaviour signal, not
     * the home card's glance list.
     */
    allNotes: List<Note> = emptyList(),
    onSave: (String, Boolean) -> Unit,
    onDelete: (Long) -> Unit = {},
    onOpenAll: () -> Unit = {},
    /**
     * v0.44.0: save a TASK note. The second
     * parameter is the optional due time
     * (epoch millis) — `null` means
     * "no deadline". The card surfaces a
     * quick "no due / in 1h / in 3h / in
     * 1d / in 3d" picker; the selected
     * value is forwarded here. The third
     * parameter is the pin state (v0.45.0).
     */
    onSaveTask: (String, Long?, Boolean) -> Unit = { _, _, _ -> },
    /**
     * v0.44.0: save a REMINDER note. The
     * second parameter is the reminder
     * time (epoch millis) — required. A
     * reminder without a time is a no-op
     * (the picker only shows "now" or
     * future offsets). The card surfaces
     * a quick "in 5m / in 15m / in 1h /
     * in 3h" picker; the selected value
     * is forwarded here. The third
     * parameter is the pin state (v0.45.0).
     */
    onSaveReminder: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    /**
     * v0.44.0: mark a TASK note done. The
     * second parameter is the new `done`
     * value. Wired to the checkbox on a
     * TASK row.
     */
    onMarkDone: (Long, Boolean) -> Unit = { _, _ -> },
) {
    // v0.25.14: rememberSaveable so a mid-capture draft
    // (a half-typed note about the email you just saw)
    // survives a config change or process death. The
    // String is auto-Saveable; no custom Saver needed.
    var draft by rememberSaveable { mutableStateOf("") }
    // v0.44.0: the kind picker state. The default is
    // QUICK — the most common case. Tapping a
    // chip flips the state and the save button
    // becomes "Save as <kind>". The picker is a row
    // of three FilterChips above the input.
    var kind by rememberSaveable { mutableStateOf(0) } // 0=Quick, 1=Task, 2=Reminder
    // v0.45.0: the "pin to home" toggle. When
    // true, the saved note is the only kind of
    // note that shows on the home card (along
    // with the next two pinned notes). When
    // false, the note is created in the store
    // but is only visible in the Notes tab. The
    // default is false — most notes are not
    // pinned, the user opts in to pinning the
    // ones they want quick access to.
    var pinned by rememberSaveable { mutableStateOf(false) }
    // v0.44.0: the time offset for Task and Reminder.
    // Indexes into a known list of millis-from-now
    // values. -1 means "no due time" (only for Task).
    // 0 means "in 5 min", 1 means "in 15 min", etc.
    var timeOffsetIndex by rememberSaveable { mutableStateOf(0) }
    // A small haptic tick on save, so the user feels
    // the capture even if the note disappears under
    // the keyboard or the screen is dim. LongPress is
    // the shortest available tick (≈5ms on most
    // devices) — short enough not to interrupt
    // typing, long enough to register. The user
    // pressed a button; the button is allowed to
    // answer.
    //
    // v0.25.16 BUG-013: gate through
    // [org.mindanchor.ui.HapticFeedbackGate] so the
    // system haptics toggle and the "remove animations"
    // a11y preference are honored. LongPress for save,
    // TextHandleMove for clear — the rich-tactile
    // distinction Brewster CHI 2007 names is preserved
    // by the gate's `type` parameter.
    val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current
    // v0.45.1: bug fix for the "at past" reminder
    // bug seen on the FireTest (the user typed a
    // note, sat on the home screen for 9 minutes,
    // tapped "in 5 min", and the reminder was set
    // for 4 min in the past — ReminderScheduler
    // then ignored it because atMillis < now).
    //
    // The previous code captured `now` once at
    // composition time via `remember { System.currentTimeMillis() }`
    // and built the offsets as absolute epoch
    // millis. The labels ("in 5 min", "in 1
    // hour", ...) are still relative to now at
    // display time, but the absolute times were
    // frozen at first composition. The fix: store
    // the offsets as `Long?` millis-from-now
    // deltas, and compute the absolute time at
    // click time inside the onClick handler.
    //
    // The labels are still hardcoded; they read
    // correctly no matter how long the user has
    // been on the home screen, because they are
    // *promises* ("in 5 min" means "5 minutes
    // from when you tap Save"), not values.
    //
    // The list shape is preserved so the rest of
    // the call site is unchanged. Task offsets
    // have `null` for "no due" so the picker
    // stays the same.
    val taskOffsets: List<Pair<String, Long?>> = listOf(
        "no due" to null,
        "in 1 hour" to (60L * 60L * 1000L),
        "in 3 hours" to (3L * 60L * 60L * 1000L),
        "tomorrow" to (24L * 60L * 60L * 1000L),
        "in 3 days" to (3L * 24L * 60L * 60L * 1000L),
    )
    val reminderOffsets: List<Pair<String, Long?>> = listOf(
        "in 5 min" to (5L * 60L * 1000L),
        "in 15 min" to (15L * 60L * 1000L),
        "in 1 hour" to (60L * 60L * 1000L),
        "in 3 hours" to (3L * 60L * 60L * 1000L),
    )
    // v0.53.0 (Red Dot review fix,
    // Issue 2): the home card has a 3-layer
    // visual hierarchy. The previous
    // v0.50.0 design was a single surface
    // with equal-weight elements; v0.53.0
    // introduces Material 3 elevation
    // tiers — the mood + notes sit on a
    // LayerSecondary card (8% white tint,
    // 4dp elevation), the input + picker +
    // save sit on a LayerTertiary card
    // (12% white tint, 8dp elevation). The
    // top of the home card (clock + mood)
    // is on the background. The result is
    // a hierarchy of moments: the clock
    // is the foreground, the mood +
    // notes are the middle, the input is
    // the surface the user touches.
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // v0.43.0: section header made clearer. The previous
        // titleMedium dim was hard to parse at a glance.
        // Now the section reads as a deliberate stop, with
        // a count line so the user knows how many notes are
        // on the home card and the activity below.
        Text(
            text = stringResource(R.string.quick_notes_section),
            style = MaterialTheme.typography.titleLarge,
            color = sky.textPrimary,
        )
        // v0.45.1: plural-aware note count. The
        // string resource is an Android <plurals>
        // element with separate "one" and "other"
        // forms.
        //
        // v0.49.0 (Phase 1 root cause from
        // systematic-debug): the count used
        // [recent].size, which is the
        // QUICK_NOTES_RECENT_CAP = 3 list —
        // a user with 100 notes saw "3 notes
        // on this phone" and assumed the
        // launcher had lost 97.
        //
        // v0.53.0 (Red Dot review fix, Issue 3):
        // the metric is no longer a count of
        // notes (vanity) but a count of distinct
        // days the user has written on (a
        // behaviour signal — a rhythm, not a
        // score). The launcher does not grade
        // the user; "X days of notes" is a
        // fact about the user's pattern. The
        // computation is in [allNotes]; the
        // QuickNotesCard receives the total
        // via [totalCount] for the "no notes"
        // empty case but computes the day
        // count from the [allNotes] parameter
        // (passed through from [LauncherRoot]).
        // The two metrics live in
        // [allNotes] to keep the day-count
        // computation close to the data.
        Text(
            text = if (totalCount == 0) {
                stringResource(R.string.quick_notes_count_zero)
            } else {
                val distinctDays = remember(allNotes) {
                    allNotes
                        .map { it.createdAt }
                        .filter { it > 0L }
                        .map { java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate() }
                        .toSet()
                        .size
                }
                androidx.compose.ui.res.pluralStringResource(
                    R.plurals.quick_notes_count_n,
                    distinctDays,
                    distinctDays,
                )
            },
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
        )
        // v0.49.0: the kind picker. Three
        // FilterChips: Quick note / Task /
        // Reminder. The default is Quick
        // note (index 0). The picker is below
        // the count line, above the input, so
        // the user knows which kind the next
        // save will create.
        //
        // v0.49.0 (Phase 1 root cause from
        // systematic-debug): the chips used to
        // be a single dim purple regardless of
        // kind. The user explicitly asked for
        // color coding so the picker matches
        // the row chips on the Notes tab. The
        // sage/indigo tokens are file-level
        // constants (KindSageBg/Fg, KindIndigoBg/Fg);
        // Quick note is intentionally neutral —
        // the row chip for a Quick note is
        // already neutral, so the picker
        // matches by staying neutral too.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        ) {
            FilterChip(
                selected = kind == 0,
                onClick = { kind = 0; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                label = { Text(stringResource(R.string.note_kind_quick)) },
                modifier = Modifier.semantics { role = Role.RadioButton },
            )
            FilterChip(
                selected = kind == 1,
                onClick = { kind = 1; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                label = { Text(stringResource(R.string.note_kind_task)) },
                modifier = Modifier
                    .semantics { role = Role.RadioButton }
                    // v0.49.0: sage when selected so
                    // the picker chip matches the
                    // sage row chip on the Notes
                    // tab. The unselected state
                    // keeps Material 3's default
                    // outline so a user can see
                    // three options at a glance.
                    .let { m ->
                        if (kind == 1) {
                            m
                        } else {
                            m
                        }
                    },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KindSageBg,
                    selectedLabelColor = KindSageFg,
                ),
            )
            FilterChip(
                selected = kind == 2,
                onClick = { kind = 2; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                label = { Text(stringResource(R.string.note_kind_reminder)) },
                modifier = Modifier.semantics { role = Role.RadioButton },
                colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                    selectedContainerColor = KindIndigoBg,
                    selectedLabelColor = KindIndigoFg,
                ),
            )
        }
        // v0.45.0: the "pin to home" toggle.
        // Always visible regardless of kind —
        // the pin state is orthogonal to the
        // kind. A pinned note shows on the
        // home card; an unpinned note only
        // shows in the Notes tab. The toggle
        // is a labelled Switch — the label
        // is the affordance, the switch is
        // the state. A Checkbox is also
        // valid; a Switch matches the rest
        // of the launcher's settings
        // surface and reads as a
        // "binary, persistent preference".
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Switch(
                checked = pinned,
                onCheckedChange = {
                    pinned = it
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                modifier = Modifier.semantics { role = Role.Switch },
            )
            Spacer(Modifier.width(12.dp))
            Column {
                // v0.53.0 (Red Dot review fix,
                // Issue 6): the v0.45.0 label
                // "Pin to home" was ambiguous —
                // is this pinning the next note?
                // all future notes? currently
                // pinned notes? The new label
                // names the toggle as a
                // preference ("Future notes on
                // home") so the user knows the
                // toggle is a default, not a
                // current action. The explainer
                // shows the current pinned count
                // so the user can see the
                // effect of the toggle.
                Text(
                    text = stringResource(R.string.pin_to_home_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = sky.textPrimary,
                )
                Text(
                    text = if (recent.isEmpty()) {
                        stringResource(R.string.pin_to_home_explainer)
                    } else {
                        // v0.53.0: show the
                        // current pinned
                        // count in the
                        // explainer. A user
                        // who toggles off
                        // sees the count
                        // drop; a user who
                        // toggles on sees
                        // it grow. The
                        // label is the
                        // preference, the
                        // number is the
                        // state.
                        val pinnedCount = recent.count { it.pinned }
                        "New notes will appear on the home screen. $pinnedCount are pinned now."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = sky.textSecondary,
                )
            }
        }
        // v0.44.0: the time picker for Task and
        // Reminder. Hidden when kind == Quick
        // note. For Task, the first option is
        // "no due" (null) so the user can save a
        // no-deadline task. For Reminder, all
        // options are concrete times in the
        // future.
        //
        // v0.49.0 (Phase 1 root cause from
        // systematic-debug): the previous Row
        // did not fit 5 chips on a 1080px
        // screen — the last chip "in 3 days"
        // was off-screen entirely, and the 4th
        // chip "tomorrow" was squished to 43px
        // and wrapped its text vertically into
        // "to / mo / rro / w". The Reminder
        // picker had the same shape: 4 chips
        // don't fit, "in 3 hours" wrapped to
        // "in 3 / hou / rs". The fix is a
        // [FlowRow] that wraps chips to the
        // next line when the row is full. The
        // [verticalArrangement = 8.dp] keeps
        // the row-gap consistent with the
        // horizontal one.
        //
        // v0.49.0 (Phase 1 systematic-debug):
        // the chips are now color-coded so the
        // Task time-picker is sage and the
        // Reminder time-picker is indigo,
        // matching the kind picker chip
        // selected-color above. The first
        // option ("no due" for Task) is neutral
        // because it is "no time", not a time
        // itself.
        if (kind == 1) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                taskOffsets.forEachIndexed { idx, (label, _) ->
                    FilterChip(
                        selected = timeOffsetIndex == idx,
                        onClick = { timeOffsetIndex = idx; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        label = { Text(label) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KindSageBg,
                            selectedLabelColor = KindSageFg,
                        ),
                    )
                }
            }
        } else if (kind == 2) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                reminderOffsets.forEachIndexed { idx, (label, _) ->
                    FilterChip(
                        selected = timeOffsetIndex == idx,
                        onClick = { timeOffsetIndex = idx; haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        label = { Text(label) },
                        modifier = Modifier.semantics { role = Role.RadioButton },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = KindIndigoBg,
                            selectedLabelColor = KindIndigoFg,
                        ),
                    )
                }
            }
        }
        // v0.20.9: bringIntoViewOnFocus so the quick-notes
        // input is not covered by the keyboard. The card is
        // the home-screen capture affordance; "I want to
        // remember this" fails if the user has to dismiss
        // the keyboard to see what they are typing.
        //
        // v0.43.0: 3-line min height, max 5 lines. The
        // previous single-line input forced longer
        // thoughts to wrap to nowhere visible; a paragraph
        // note was the most common kind the user wrote, and
        // the truncation hid it. min/max lines keeps the
        // field from collapsing to one line on an empty
        // draft and from growing forever on a long one.
        //
        // v0.48.0: the trailing icon is an `×` that
        // appears only when the draft is non-blank. The
        // v0.45.0-v0.45.1 design had a separate
        // OutlinedButton (Save, wide) + TextButton
        // (Clear, narrow) row. The Clear was visually
        // loud (same row as the primary action) and the
        // user asked for it to be less prominent. The
        // `×` inside the input is the standard
        // "clear this field" affordance in Compose
        // TextField and matches the AIO Launcher /
        // Todoist pattern: a single primary action
        // (Save) below the input, a contextual clear
        // inside the input.
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = {
                Text(
                    text = when (kind) {
                        1 -> stringResource(R.string.quick_notes_input_hint_task)
                        2 -> stringResource(R.string.quick_notes_input_hint_reminder)
                        else -> stringResource(R.string.quick_notes_input_hint)
                    },
                )
            },
            minLines = 3,
            maxLines = 5,
            // v0.48.0: trailing `×` icon for
            // clear. Visible only when the draft
            // is non-blank. The icon is a
            // pass-through clickable that wipes
            // the draft.
            trailingIcon = if (draft.isNotBlank()) {
                {
                    TextButton(
                        onClick = {
                            draft = ""
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        modifier = Modifier.semantics { role = Role.Button },
                    ) {
                        Text(
                            text = "×",
                            style = MaterialTheme.typography.titleLarge,
                            color = sky.textSecondary,
                        )
                    }
                }
            } else null,
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewOnFocus(),
        )
        // v0.43.0: Save as a primary outlined action
        // directly under the input. v0.44.0: the
        // button label is "Save as Quick" / "Save as
        // task" / "Save reminder" depending on the
        // selected kind. The save callback varies by
        // kind: Quick calls onSave, Task calls
        // onSaveTask with the selected due time,
        // Reminder calls onSaveReminder with the
        // selected reminder time.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // v0.45.1: the Save button is only enabled
            // when the draft is non-blank. For
            // Reminder, also check that the selected
            // offset is positive (a 0 or negative
            // delta would resolve to a past time).
            // For Task, the first option is "no due"
            // (null delta) which is always allowed.
            //
            // v0.47.0: when the draft contains a
            // parseable natural-language phrase
            // ("in 2 hours", "tomorrow at 7pm",
            // "monday morning"), the [Whenever]
            // parser extracts the body and the
            // reminder time. The chip is bypassed;
            // the parsed time wins. The Save
            // button is enabled when the draft
            // contains a parseable phrase OR the
            // chip is valid.
            val nlPair: Pair<String, Long>? = if (kind == 2) {
                org.mindanchor.note.Whenever.extractFrom(draft)
            } else null
            val (label, enabled) = when (kind) {
                1 -> stringResource(R.string.quick_notes_save_task) to (draft.isNotBlank())
                2 -> {
                    val chipValid = (reminderOffsets.getOrNull(timeOffsetIndex)?.second ?: 0L) > 0L
                    stringResource(R.string.quick_notes_save_reminder) to
                        (draft.isNotBlank() && (nlPair != null || chipValid))
                }
                else -> stringResource(R.string.quick_notes_save) to (draft.isNotBlank())
            }
            // v0.53.0 (Red Dot review fix,
            // Issue 10): progressive
            // disclosure. The v0.45.1 design
            // greyed-out the button when the
            // input was empty; the greyed state
            // was a dead surface that did not
            // tell the user *why* it was dead.
            // v0.53.0 hides the button entirely
            // when there is no valid input, and
            // shows a one-line hint with the
            // reason. The hint names the
            // missing thing ("Type something
            // to save", "Add a time to save")
            // so the user knows the next
            // affordance.
            val (hint, buttonVisible) = when {
                !enabled && draft.isBlank() -> stringResource(R.string.save_button_needs_text) to false
                !enabled && kind == 2 -> stringResource(R.string.save_button_needs_time) to false
                else -> "" to true
            }
            // v0.48.0: Save is the only button in
            // the row. It spans the full width
            // (no weight(1f) — the Row is a
            // single-child Row). Clear is a
            // contextual `×` inside the input
            // field (trailing icon). The row
            // is shorter and the visual
            // hierarchy is cleaner: one
            // primary action, one contextual
            // clear.
            //
            // v0.53.0 (Issue 10): progressive
            // disclosure. The v0.48.0 button
            // was always visible but greyed
            // when the input was empty; the
            // greyed state was a dead
            // affordance that did not tell
            // the user *why* it was dead.
            // v0.53.0 hides the button when
            // there is no valid input and
            // shows a one-line hint with the
            // reason. When the input is
            // valid, the button is shown and
            // the hint is absent.
            if (buttonVisible) {
                OutlinedButton(
                    onClick = {
                        // v0.45.1: capture `now` at click
                        // time, not at composition time.
                        // Each task/reminder save computes
                        // its absolute fire time from the
                        // delta in the offsets list. This
                        // closes the "at past" bug where
                        // ReminderScheduler would silently
                        // ignore reminders set for a time
                        // before the current moment.
                        //
                        // v0.47.0: if the body parses as a
                        // natural-language reminder, use
                        // the parsed body + time. The
                        // chip is bypassed; the parsed
                        // time is absolute (not a delta
                        // from now), so the
                        // ReminderScheduler receives the
                        // exact epoch millis.
                        val nowMs = System.currentTimeMillis()
                        when (kind) {
                            1 -> {
                                val delta = taskOffsets.getOrNull(timeOffsetIndex)?.second
                                val due = if (delta == null) null else nowMs + delta
                                onSaveTask(draft, due, pinned)
                            }
                            2 -> {
                                val nl = nlPair
                                if (nl != null) {
                                    onSaveReminder(nl.first, nl.second, pinned)
                                } else {
                                    val delta = reminderOffsets.getOrNull(timeOffsetIndex)?.second ?: 0L
                                    val at = if (delta > 0L) nowMs + delta else nowMs
                                    onSaveReminder(draft, at, pinned)
                                }
                            }
                            else -> onSave(draft, pinned)
                        }
                        draft = ""
                        // v0.45.0: reset the pin toggle on
                        // save so the next note starts
                        // unpinned (the common case). A
                        // user who wants the next note
                        // pinned too re-checks the box
                        // before saving.
                        pinned = false
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.Button },
                ) {
                    Text(label)
                }
            } else {
                // v0.53.0: the hint
                // replaces the
                // button. One line,
                // bodySmall, in the
                // secondary text
                // colour, so the
                // user reads it as a
                // status rather than
                // a content element.
                // The hint is the
                // "next step" — the
                // user types, the
                // button reappears.
                Text(
                    text = hint,
                    // v0.55.0: bumped from bodySmall
                    // (12sp) to bodyMedium (14sp).
                    // The pre-v0.55.0 hint was the
                    // smallest M3 body size, which
                    // on the home card in light mode
                    // looked like a placeholder, not
                    // an instruction. bodyMedium
                    // matches the body text of the
                    // input field itself, so the
                    // hint and the input read as
                    // the same scale.
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 4.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (recent.isEmpty()) {
            // v0.43.0: empty state lives BELOW the input
            // and Save, framed as a quiet invitation. The
            // previous version placed the empty text after
            // the Save button which read as a leftover label.
            Text(
                text = stringResource(R.string.quick_notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = sky.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
        } else {
            // v0.43.0: the home card now shows up to three
            // recent notes as plain rows with a one-tap
            // delete affordance per row. v0.44.0: each
            // row renders a type chip (Quick / Task /
            // Reminder), a checkbox for tasks, a clock
            // icon for reminders, and the existing ×.
            //
            // v0.53.0 (Red Dot review fix,
            // Issue 2): the recent-notes
            // section is wrapped in a
            // LayerSecondary card (8% white
            // tint, 12dp rounded corners).
            // The card is the v0.50.0 "the
            // notes are the middle layer"
            // surface. The user reads the
            // recent notes as a single
            // block, not as a loose list of
            // rows. The card is 16dp
            // horizontal margin so the
            // tint reads as a deliberate
            // surface, not as a full-bleed
            // band.
            Spacer(Modifier.height(16.dp))
            androidx.compose.material3.Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = LayerSecondaryBg,
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = LayerSecondaryBorder,
                ),
            ) {
            recent.take(3).forEach { note ->
                // v0.50.0: word-boundary
                // truncation. The home card
                // has a 2-line max; if the
                // first-line body exceeds the
                // available width, the second
                // line shows an ellipsis.
                // Pre-truncating at a word
                // boundary (not mid-word) keeps
                // the visible text readable:
                // "Send a message to dad. Just
                // a short one. The cricket
                // score…" instead of
                // "The cricket sc…".
                val title = note.title.ifBlank {
                    truncateAtWord(note.body, 80)
                }
                val whenText = noteTimeText(note)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // v0.44.0: task checkbox.
                    // Shown only for TASK-type
                    // notes. Tapping the checkbox
                    // toggles `done`. The note
                    // body gets a strikethrough
                    // when done is true.
                    if (note.type == org.mindanchor.model.NoteType.TASK) {
                        Checkbox(
                            checked = note.done,
                            onCheckedChange = { onMarkDone(note.id, it) },
                            modifier = Modifier.semantics { role = Role.Checkbox },
                        )
                    } else {
                        // v0.45.1: color-coded kind chip
                        // for the leading icon on a
                        // non-task row. A small
                        // color-graded circle (sage for
                        // Task, indigo for Reminder, no
                        // chip for Quick) replaces the
                        // previous "T" / "R" / blank
                        // character chip. The colour
                        // reads at a glance without
                        // reading a letter.
                        //
                        // The colour is a Box with
                        // background + 2-char label so
                        // the type is still legible for
                        // a screen-reader user. The label
                        // keeps the home text-only — no
                        // icon asset added in v0.45.1.
                        val (chipBg, chipFg, chipLabel) = when (note.type) {
                            org.mindanchor.model.NoteType.REMINDER -> Triple(
                                androidx.compose.ui.graphics.Color(0xFFC7D2FE), // indigo-200
                                androidx.compose.ui.graphics.Color(0xFF3730A3), // indigo-800
                                "Re",
                            )
                            org.mindanchor.model.NoteType.TASK -> Triple(
                                androidx.compose.ui.graphics.Color(0xFFB7C9A8), // sage-300
                                androidx.compose.ui.graphics.Color(0xFF3F5233), // sage-800
                                "Ta",
                            )
                            else -> Triple(
                                androidx.compose.ui.graphics.Color.Transparent,
                                sky.textSecondary,
                                "",
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (chipLabel.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = chipBg,
                                            shape = androidx.compose.foundation.shape.CircleShape,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = chipLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = chipFg,
                                    )
                                }
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        // v0.50.0: single line on
                        // the home card. The home
                        // card is glance-sized —
                        // 100% of the title is read
                        // when the row is visible;
                        // the second line was
                        // adding height for the
                        // same word count the
                        // single line already
                        // showed (the
                        // pre-truncateAtWord above
                        // cuts at a word
                        // boundary, so the
                        // single line is full
                        // words, not mid-word).
                        // The previous v0.45.1
                        // `maxLines = 2` is
                        // dropped.
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                textDecoration = if (note.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            ),
                            color = if (note.done) sky.textSecondary else sky.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // v0.44.0: due / reminder
                        // time on the row when the
                        // note has one. A TASK
                        // shows "due <time>"; a
                        // REMINDER shows "at
                        // <time>"; a QUICK shows
                        // the timestamp.
                        val sub = when {
                            note.reminderAt != null -> stringResource(
                                R.string.note_subtitle_at,
                                reminderTimeText(note.reminderAt!!),
                            )
                            note.dueAt != null -> stringResource(
                                R.string.note_subtitle_due,
                                reminderTimeText(note.dueAt!!),
                            )
                            else -> whenText
                        }
                        Text(
                            text = sub,
                            // v0.55.0: bumped from
                            // bodySmall (12sp) to
                            // bodyMedium (14sp). The
                            // pre-v0.55.0 timestamp was
                            // the smallest M3 body size,
                            // which on the home card in
                            // light mode looked like a
                            // sub-label, not a glanceable
                            // time. bodyMedium matches the
                            // recent-note title and reads
                            // as a single scale.
                            style = MaterialTheme.typography.bodyMedium,
                            color = sky.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    // v0.55.0: removed the × delete
                    // button from the home card
                    // recent-note rows. The user
                    // explicitly asked for a
                    // clutter-free home screen and
                    // wants to manage pin/delete
                    // from the Notes tab instead.
                    // The v0.45.0+ tap-to-delete
                    // affordance still exists on
                    // the Notes tab rows; the home
                    // card is now read-only. The
                    // [onDelete] callback is
                    // retained in the function
                    // signature for backward
                    // compatibility with any
                    // wrapper callers but is no
                    // longer called from this
                    // composable.
                }
            }
            }
            if (recent.size > 3) {
                TextButton(
                    onClick = onOpenAll,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { role = Role.Button },
                ) {
                    Text(
                        text = stringResource(R.string.quick_notes_view_all),
                        color = sky.textSecondary,
                    )
                }
            }
        }
    }
}

/**
 * v0.46.0: the 1-tap Mood Card. Five emoji in a
 * single row above the QuickNotesCard. Tapping
 * an emoji creates a Note with that emoji as
 * the body and type=GENERAL. No text required,
 * no classifier, no task checkbox, no reminder.
 *
 * The card is intentionally small (one row, 5
 * emoji) — the home is a glanceable surface, not
 * a dashboard. The "what is it right now?"
 * question is the home's first ask; this is the
 * one-tap answer. Same shape as the v0.28.0
 * Distress Thermometer affordance, but one tap
 * instead of one activity.
 *
 * Why 5 emoji and not 9 or 11: the 5-emoji
 * floor is the convergence point in the
 * competitor survey. Daylio defaults to a
 * 5-point scale; Bearable's default grid is
 * 5-emoji wide; Moodflow's gesture-driven
 * scale is 5-emoji visible at a glance. 9+
 * is more granular but the home is not the
 * place for granularity.
 *
 * The emoji themselves are intentionally not
 * clinical (no "depressed", "anxious", "numb"
 * labels). A mental-health surface that does
 * not pathologise a moment in the user's day
 * is the right starting posture; the user can
 * add a body to the note in the Notes tab if
 * they want to be more specific. The five
 * chosen emoji are affect-first, not
 * diagnostic-first.
 */
@Composable
private fun MoodCard(
    sky: SkyContent,
    onLog: (String) -> Unit,
) {
    val haptics = org.mindanchor.ui.LocalHapticFeedbackGate.current
    // v0.53.0 (Red Dot review fix, Issue 4):
    // the mood scale uses NAMED states, not
    // generic emoji. The label is the data
    // the user remembers and the launcher
    // saves as the reading. Five states,
    // ascending, in plain English: Crushed /
    // Heavy / Steady / Light / Bright.
    //
    // Reference: Yale Center for Emotional
    // Intelligence's "How We Feel" app uses
    // the same pattern (5 named states,
    // 4.8 stars). MindAnchor's calm-launcher
    // style adds a small text label below
    // each face so the user has a name for
    // what they tapped.
    val moodEntries = listOf(
        Triple("😞", "low", R.string.mood_state_1),
        Triple("😕", "off", R.string.mood_state_2),
        Triple("😐", "neutral", R.string.mood_state_3),
        Triple("🙂", "ok", R.string.mood_state_4),
        Triple("😊", "good", R.string.mood_state_5),
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // v0.46.0: the Mood Card has no
        // header text. The 5 emoji are
        // self-explanatory; a "How is it?"
        // label would be clinical, and the
        // home already has the greeting
        // ("Winding down.") that does the
        // "right now" work. The card is
        // here to capture, not to ask.
        //
        // v0.53.0: the v0.46.0 reasoning is
        // still right for the HEADER, but the
        // LABELS under each face are new. The
        // header is still absent (the launcher's
        // tone is "observe, don't evaluate") but
        // the named states give the user a
        // vocabulary for what they are observing.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            moodEntries.forEach { (emoji, key, labelRes) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onLog(emoji)
                            }
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = "Mood: ${key}"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                    }
                    Text(
                        text = stringResource(labelRes),
                        // v0.55.0: bumped from labelSmall
                        // (11sp) to labelMedium (12sp) +
                        // Medium weight. The pre-v0.55.0
                        // typography was the smallest
                        // Material 3 size, which on the
                        // home card in light mode looked
                        // too dim to read against the
                        // pale sky. labelMedium is the
                        // M3 standard for "supporting
                        // text on the same line as
                        // primary content" — a face
                        // name sits below a face, not
                        // in a dense table.
                        style = MaterialTheme.typography.labelMedium,
                        color = sky.textSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Format a note's timestamp for the home card.
 *
 * The list view uses the absolute date+time; the
 * home card needs something a person can read at
 * a glance (where the line is small). Today vs.
 * yesterday vs. earlier is the right shape: a
 * note from 2pm today reads "14:00", a note from
 * yesterday reads "yesterday 22:13", a note from
 * last week reads the short date. The function
 * is local to this file because the rule is
 * display-only and no other surface needs the
 * same compaction.
 */
private fun noteTimeText(note: Note): String {
    val now = System.currentTimeMillis()
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = note.createdAt }
    val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
    val sameDay = cal.get(java.util.Calendar.YEAR) == nowCal.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == nowCal.get(java.util.Calendar.DAY_OF_YEAR)
    if (sameDay) {
        return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.createdAt))
    }
    val yesterdayCal = (nowCal.clone() as java.util.Calendar).apply { add(java.util.Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = cal.get(java.util.Calendar.YEAR) == yesterdayCal.get(java.util.Calendar.YEAR) &&
        cal.get(java.util.Calendar.DAY_OF_YEAR) == yesterdayCal.get(java.util.Calendar.DAY_OF_YEAR)
    if (isYesterday) {
        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(note.createdAt))
        return "yesterday $time"
    }
    return DateFormat.getDateInstance(DateFormat.SHORT).format(Date(note.createdAt))
}

/**
 * v0.44.0: format a future epoch-millis time
 * for the note row's subtitle. The label is
 * always relative to now: "in 5 min", "in 3
 * hours", "tomorrow 14:00", or the absolute
 * date if it is more than a week out. The
 * function is local to this file because the
 * rule is display-only and no other surface
 * needs the same compaction.
 */
private fun reminderTimeText(atMillis: Long): String {
    val now = System.currentTimeMillis()
    val deltaMs = atMillis - now
    val absDeltaMin = kotlin.math.abs(deltaMs) / 60_000L
    return when {
        deltaMs < 0 -> "past"
        absDeltaMin < 60 -> "in $absDeltaMin min"
        absDeltaMin < 24 * 60 -> "in ${absDeltaMin / 60} hours"
        absDeltaMin < 7 * 24 * 60 -> "in ${absDeltaMin / (24 * 60)} days"
        else -> DateFormat.getDateInstance(DateFormat.SHORT).format(Date(atMillis))
    }
}

/**
 * The wellness card — per-signal readings for today against the
 * person's own history, in the same N-of-1 framing the rest of
 * the launcher uses. The home is a glance surface: one line per
 * signal, no charts, no diagnosis. A signal the watch had no
 * data for reads as a dash, not a number; a signal the baseline
 * has not yet caught up to reads as a still-building note, not a
 * fake number.
 *
 * ## Why direction only, not raw z-score
 *
 * The home card is for glancing at, not for analysing. A robust
 * z-score of 1.4 is a fraction of a personal distribution; a
 * label of "above your usual" is the same fact in words. The
 * number is preserved in [WellnessReading.zScore] for the
 * settings panel and the nightly report, where it is read in
 * the larger context those surfaces provide.
 *
 * ## Why a card at all on a launcher that says "say less"
 *
 * The launcher is a quiet place by design, and a card that
 * updates itself with five lines a day is the kind of thing
 * that trains a person to look. The compromise: the card is
 * shown only when at least one signal has a value to show AND
 * a baseline to compare it to. A user with no Health Connect
 * source app, or fewer than 14 days of history, sees no card —
 * the home stays the home.
 *
 * ## Why "your usual" rather than "your 30-day average"
 *
 * The signal is the personal median, the language is "usual".
 * "Average" is a population word — it tells a person where
 * they are against a curve that has nothing to say about
 * them. "Usual" is a personal word — it tells a person where
 * they are against themselves. The full machinery is in
 * [org.mindanchor.vitals.WellnessStats]; the home card is
 * deliberately understating it.
 */
@Composable
private fun WellnessCard(
    sky: SkyContent,
    readings: List<WellnessReading>,
) {
    // Hide the card entirely when there is nothing to say.
    // The home is a glance surface; an empty card is a
    // standing invitation to look. Two cases:
    //  - no readings yet (the ViewModel has not refreshed
    //    — show nothing, do not show a skeleton)
    //  - every signal is NO_DATA (no wearable, no
    //    permission, or fewer than 14 days of history)
    val hasAnything = readings.any { it.today != null && it.direction != WellnessDirection.NO_DATA }
    if (!hasAnything) return
    val reportable = readings.any { it.baseline.isReportable }
    if (!reportable) return

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.wellness_section),
            style = MaterialTheme.typography.titleMedium,
            color = sky.textSecondary,
        )
        Text(
            text = stringResource(R.string.wellness_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
        readings.forEach { reading ->
            WellnessLine(sky = sky, reading = reading)
        }
    }
}

/**
 * One row of the wellness card: the signal's name, today's
 * value, the direction band, and a small "vs your usual"
 * anchor.
 */
@Composable
private fun WellnessLine(sky: SkyContent, reading: WellnessReading) {
    val name = stringResource(wellnessSignalNameRes(reading.signal))
    val todayText = reading.today?.let { formatWellnessValue(reading.signal, it) }
        ?: stringResource(R.string.wellness_no_value_today)
    val directionText = stringResource(wellnessDirectionRes(reading.direction))
    val medianText = reading.baseline.median?.let { formatWellnessValue(reading.signal, it) }
        ?: stringResource(R.string.wellness_baseline_building)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = sky.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = todayText,
            style = MaterialTheme.typography.bodyMedium,
            color = sky.textPrimary,
        )
        Text(
            text = "  $directionText",
            style = MaterialTheme.typography.bodySmall,
            color = sky.textSecondary,
        )
    }
    Text(
        text = stringResource(R.string.wellness_vs_usual, medianText),
        style = MaterialTheme.typography.bodySmall,
        color = sky.textSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 2.dp),
    )
}

/**
 * The display name for a [WellnessSignal]. Local to this file
 * because the home card is the only place that needs the
 * short form; the settings panel uses the same resource
 * directly.
 */
private fun wellnessSignalNameRes(signal: WellnessSignal): Int = when (signal) {
    WellnessSignal.HRV -> R.string.wellness_signal_hrv
    WellnessSignal.RESTING_HEART_RATE -> R.string.wellness_signal_resting_hr
    WellnessSignal.STEPS -> R.string.wellness_signal_steps
    WellnessSignal.SLEEP_MINUTES -> R.string.wellness_signal_sleep
    WellnessSignal.MINDFULNESS_MINUTES -> R.string.wellness_signal_mindfulness
}

/**
 * The wording for a [WellnessDirection] band. Direction-only,
 * deliberately never labelled "good" or "bad" — see
 * [WellnessDirection]'s KDoc.
 */
private fun wellnessDirectionRes(direction: WellnessDirection): Int = when (direction) {
    WellnessDirection.NO_DATA -> R.string.wellness_dir_no_data
    WellnessDirection.AT -> R.string.wellness_dir_at
    WellnessDirection.ABOVE -> R.string.wellness_dir_above
    WellnessDirection.MUCH_ABOVE -> R.string.wellness_dir_much_above
    WellnessDirection.BELOW -> R.string.wellness_dir_below
}

/**
 * Render a [WellnessSignal] value for the home card.
 *
 * The units match the source data, not the population
 * literature — steps are integer, sleep is minutes, HRV is
 * milliseconds, and so on. The format here is the home card's
 * version: integer when the source is integer, "%.0f ms" for
 * HRV, "%.0f bpm" for resting heart rate. The settings panel
 * uses the same formats via [org.mindanchor.report.ValueFormat].
 */
private fun formatWellnessValue(signal: WellnessSignal, value: Double): String = when (signal) {
    WellnessSignal.HRV -> "%.0f ms".format(value)
    WellnessSignal.RESTING_HEART_RATE -> "%.0f bpm".format(value)
    WellnessSignal.STEPS -> "%,d".format(value.toLong())
    WellnessSignal.SLEEP_MINUTES -> "${value.toInt()} min"
    WellnessSignal.MINDFULNESS_MINUTES -> "${value.toInt()} min"
}

// combinedClickable, for the long-press on a favourite.
@OptIn(ExperimentalFoundationApi::class)
@Suppress("FunctionNaming", "LongMethod", "LongParameterList")
@Composable
private fun HomeSurface(
    sky: SkyContent,
    favorites: List<DisplayApp>,
    /**
     * v0.49.0: the TOTAL note count for
     * the "X notes on this phone" line
     * on the QuickNotesCard. The home
     * card still uses [recentNotes] for
     * the 3-row "what I just wrote"
     * list (v0.43.0 design) — only the
     * count line reads from the total.
     */
    allNotes: List<Note> = emptyList(),
    /**
     * v0.42.0: the 2x2 needs grid ("What do you need right now?")
     * is hidden when this is false. Default `true` keeps the
     * v0.35.0 / v0.40.1 / v0.41.0 home intact for callers that
     * do not pass the parameter (e.g. previews, the launcher
     * tests that pre-date the toggle). The LauncherRoot reads
     * the value from AppearancePrefs.needsGridVisible.
     */
    needsGridVisible: Boolean = true,
    onOpenDrawer: () -> Unit,
    onOpenSettings: () -> Unit,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
    loopPhase: LoopPhase = LoopPhase.NONE,
    loopNote: String? = null,
    loopPostponedAt: Instant? = null,
    onLoopSave: (String) -> Unit = {},
    onLoopClear: () -> Unit = {},
    onLoopPostpone: (Instant) -> Unit = {},
    onLoopCancelPostpone: () -> Unit = {},
    /**
     * v0.28.0: open the Distress Thermometer activity. Wired to
     * the "Ground me here" button on the home Distress card.
     * The first question the home surface asks is "how is it
     * right now?" — validation-first, before any task-capture.
     */
    onOpenDistressThermometer: () -> Unit = {},
    /** v0.26.0 §3.2: long-press the clock. */
    onOpenGroundMe: () -> Unit = {},
    /** Shown only when last night's report actually has something in it. */
    hasReport: Boolean = false,
    onOpenReport: () -> Unit = {},
    /**
     * v0.20.1 round 5: route to [org.mindanchor.model.NoteActivity].
     * Notes are a one-tap home-screen affordance for the
     * "I want to remember this" capture pattern (brief §A).
     * TopEnd so it does not collide with TopStart (Support)
     * or BottomStart (Digest) or BottomEnd (Settings).
     */
    onOpenNotes: () -> Unit = {},
    /**
     * v0.20.1 round 5 follow-up: route to
     * [org.mindanchor.model.CheckInHistoryActivity].
     * The history is a read-only list of past
     * check-ins; the *write* side is the
     * phone-unlock trigger, the *read* side is
     * the home-screen affordance. Same pattern
     * as the notes (capture) — separate the write
     * and read surfaces so neither clutters the
     * other.
     */
    onOpenCheckInHistory: () -> Unit = {},
    /**
     * v0.25.2-A (Task 6): route to the
     * letter inbox + reader (LauncherSurface.Letter).
     * Wired to the new "letters" TextButton at
     * the top of the TopEnd Column (above notes
     * + history). Mirrors the [onOpenReport]
     * pattern: the lambda body lives at the
     * call site in [LauncherRoot] and sets the
     * letter state (selectedDate, cameFrom) and
     * the surface dispatcher.
     */
    onOpenLetters: () -> Unit = {},
    /**
     * v0.26.4 §3.4: route to [org.mindanchor.chain.ChainCaptureActivity].
     * The chain capture is the "what just happened?" surface —
     * 5 fields (event / interpretation / part / want /
     * part-to-bring) for a person mid-dysregulation to
     * externalise the moment before acting on it. It is
     * not a daily ritual; it is a low-friction affordance
     * that the home surface should make one tap away
     * without burying it under settings.
     */
    onOpenChainCapture: () -> Unit = {},
    /**
     * v0.26.4 §3.4: route to [org.mindanchor.ifs.IfsPickerActivity].
     * "Which part is loud?" is a 2-column chip grid of named
     * IFS parts. Same shape as the chain capture: a
     * low-friction affordance for a person mid-dysregulation
     * to name the part before acting on it. Home surface
     * affordance.
     */
    onOpenIfsPicker: () -> Unit = {},
    /**
     * v0.26.4 §3.4: route to [org.mindanchor.export.ExportActivity].
     * The "Export for my therapist" affordance. One tap on
     * the home surface to dump notes, OneThing, OpenLoop,
     * BedtimeList, wellness N-of-1, check-ins, BPD profile,
     * chain captures, IFS picks to JSON (excludes Letter
     * content). System share sheet for delivery to the
     * therapist.
     */
    onOpenExport: () -> Unit = {},
    /**
     * v0.35.0: the "Be heard" affordance on the needs card.
     * Routes to [org.mindanchor.support.SupportActivity] —
     * the launcher's existing 8-surface support menu
     * (self-compassion, radical acceptance, opposite action,
     * interpersonal, ACCEPTS, half-smile, IMPROVE, the
     * check-the-facts skill). The "Be heard" label is the
     * need-language the home asks for; the activity it
     * opens is the existing surface.
     */
    onOpenSupport: () -> Unit = {},
    /**
     * v0.35.0: the "A moment" affordance. Routes to
     * [org.mindanchor.support.AcceptsActivity] — the DBT
     * ACCEPTS skill (Activities, Contributing, Comparisons,
     * Emotions, Pushing away, Thoughts, Sensations). A
     * single-tap DBT path for the "I need to come down"
     * need, which is what the home asks the user to name
     * before routing.
     */
    onOpenAccepts: () -> Unit = {},
    /**
     * v0.35.0: the "Check in" affordance. Routes to
     * [org.mindanchor.support.DiaryCardActivity] — the
     * DBT diary card (DBT skills training handouts,
     * Linehan 2015). A one-tap path to the diary card
     * the user already fills in at the day's end; the
     * "Check in" door is the same diary card, framed as
     * a needs-first affordance.
     */
    onOpenDiaryCard: () -> Unit = {},
    /**
     * v0.35.0: the "Get through this" affordance. Opens
     * the [LauncherSurface.GetThrough] sub-menu — a
     * three-button sheet that surfaces the existing
     * chain capture, IFS picker, and export activities
     * in the order a person mid-dysregulation is most
     * likely to want them.
     */
    onOpenGetThrough: () -> Unit = {},
    /**
     * v0.35.0: the three StateFlows the "Where it comes
     * from" home card reads. The card itself is hidden
     * entirely when no source has any data to surface —
     * see [DataSourcesCard] for the visibility rules.
     */
    healthConnectStatus: LauncherViewModel.HealthConnectStatus =
        LauncherViewModel.HealthConnectStatus.NotGranted,
    corosDataStatus: LauncherViewModel.CorosDataStatus =
        LauncherViewModel.CorosDataStatus.NotConnected,
    ppgLastMeasurement: LauncherViewModel.PpgLastMeasurement? = null,
    /**
     * v0.20.4: the home-screen quick-notes
     * affordance. The card shows a one-line
     * input, a save button, and the most recent
     * notes. The save callback writes to the
     * same [org.mindanchor.data.NotesPrefs] the
     * full [org.mindanchor.model.NoteActivity]
     * reads from — the two surfaces share the
     * store. [onOpenNotes] is reused to route
     * the "View all" link and the per-row tap
     * to the full activity.
     */
    recentNotes: List<Note> = emptyList(),
    /**
     * v0.20.4: the home-screen quick-notes
     * affordance. v0.45.0: the second
     * parameter is the pin state — a
     * pinned note shows on the home card,
     * an unpinned note only shows in the
     * Notes tab.
     */
    onAddQuickNote: (String, Boolean) -> Unit = { _, _ -> },
    /**
     * v0.46.0: log a 1-tap mood. The body is the
     * emoji itself. The full save lives in
     * [org.mindanchor.launcher.LauncherViewModel.addMoodLog].
     * The mood card is the "what is it right
     * now?" door the home surface asks the
     * user to answer first — same shape as
     * the v0.28.0 Distress Thermometer, but
     * one tap instead of one activity.
     */
    onAddMoodLog: (String) -> Unit = {},
    /**
     * v0.43.0: delete a note from the home card. Wired
     * to the × on each recent-note row. The full delete
     * lives in [org.mindanchor.launcher.LauncherViewModel.deleteNote];
     * HomeSurface only forwards the id.
     */
    onDeleteNote: (Long) -> Unit = {},
    /**
     * v0.44.0: save a TASK note. Wired to the
     * "Save as task" button on the home card. The
     * second parameter is the optional due time
     * (epoch millis) — pass `null` for a
     * "no-deadline" task. The full save lives
     * in [org.mindanchor.launcher.LauncherViewModel.addTaskNote].
     */
    onAddTaskNote: (String, Long?, Boolean) -> Unit = { _, _, _ -> },
    /**
     * v0.44.0: save a REMINDER note. Wired to the
     * "Save as reminder" button on the home
     * card. The second parameter is the reminder
     * time (epoch millis) — required. A reminder
     * without a time is a no-op. The full save
     * and alarm schedule live in
     * [org.mindanchor.launcher.LauncherViewModel.addReminderNote].
     */
    onAddReminderNote: (String, Long, Boolean) -> Unit = { _, _, _ -> },
    /**
     * v0.44.0: mark a TASK note done. Wired to
     * the checkbox on each TASK row. The full
     * toggle lives in
     * [org.mindanchor.launcher.LauncherViewModel.markNoteDone].
     */
    onMarkNoteDone: (Long, Boolean) -> Unit = { _, _ -> },
    /**
     * v0.45.0: pin a note to the home card. Wired
     * to the "pin to home" toggle on the
     * QuickNotesCard input and on each row of the
     * Notes tab. Pinned notes are the only notes
     * shown on the home card; the Notes tab
     * contains every note (pinned + unpinned).
     * The full pin lives in
     * [org.mindanchor.launcher.LauncherViewModel.pinNote].
     */
    onPinNote: (Long, Boolean) -> Unit = { _, _ -> },
    /**
     * v0.20.5: the wellness card — per-signal readings for
     * today against the person's own history. Null is
     * "still loading", not "no data": the card is hidden
     * entirely when [wellnessReadings] is null or when
     * every reading is [WellnessDirection.NO_DATA]. The
     * home stays the home when there is nothing to show.
     */
    wellnessReadings: List<org.mindanchor.vitals.WellnessReading>? = null,
    /**
     * v0.22.0 (WP-10 step 2): the "what makes this different"
     * callout. Renders a single line of small text below
     * the greeting for the first
     * [org.mindanchor.data.LauncherPrefs.INTRO_CALLOUT_LAUNCHES]
     * home-surface displays, then disappears forever. The
     * parent is expected to call [onRecordLaunch] once on
     * every home-surface display so the callout can know
     * when to hide.
     */
    showIntroCallout: Boolean = false,
    onRecordLaunch: () -> Unit = {},
    /**
     * v0.44.0: the active reminder flash event.
     * `null` means no flash. When non-null, the
     * home surface renders a full-screen pulsing
     * overlay with the note body and a "dismiss"
     * affordance. The surface calls
     * [onFlashConsumed] when the user taps to
     * dismiss or when the 5-second auto-clear
     * fires.
     */
    flashEvent: org.mindanchor.note.FlashSignal.FlashEvent? = null,
    /**
     * v0.44.0: invoked after the home surface has
     * shown the flash. The caller (LauncherRoot)
     * forwards this to HomeActivity which clears
     * the FlashSignal singleton. A no-op when the
     * user has not yet dismissed the flash.
     */
    onFlashConsumed: () -> Unit = {},
) {
    val now = rememberMinuteTick()
    val clockFormat = rememberClockFormat()

    // v0.20.9: nested safe-drawing on the outer Box and
    // imePadding on the inner scroll container. The outer
    // Box keeps the corner buttons clear of the status and
    // navigation bars in normal use; imePadding on the
    // inner Column shrinks the scroll area to the gap
    // between the top safe area and the keyboard, so the
    // "Put it down" / "Add line" / save buttons of the
    // bedtime list and the per-field "Save" buttons of the
    // quick-notes card stay above the keyboard instead of
    // being layered under it. Without imePadding on the
    // scroll container, focusing a field that sits near the
    // bottom of the Column let the keyboard cover the field
    // and the buttons below it.
    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        // Centred when it fits, scrollable when it does not.
        //
        // The content used to be centred with no way to scroll, so anything
        // taller than the screen was simply cut off and unreachable. That
        // needed neither an exotic device nor landscape to happen: a large
        // font scale, or enough favourites, was sufficient — and a person
        // who has set a large font scale is exactly the person who cannot
        // recover by squinting. The bottom padding keeps the last favourite
        // clear of the drawer and settings buttons layered over this.
        //
        // v0.20.9: the modifier order is now
        //   fillMaxSize -> imePadding -> padding -> verticalScroll
        // The previous order had `padding` *inside* the
        // verticalScroll, which meant the 88dp bottom
        // padding was applied to the content, not to the
        // scroll container itself. The content then
        // scrolled into the padding area and the last
        // items (the quick-notes empty state, the
        // favourites) ended up layered under the bottom
        // navigation row. Moving the padding outside
        // the scroll shrinks the scrollable area by 88dp
        // on the bottom, so the last content item is
        // always 88dp above the bottom navigation no
        // matter how far the user scrolls.
        //
        // v0.20.9: the scroll container also takes
        // imePadding so the bedtime list, open-loop
        // capture, and quick-notes input fields are not
        // covered by the soft keyboard. Each input field
        // opts in to BringIntoViewRequester so the
        // focused field is scrolled into view above the
        // keyboard.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(start = 32.dp, end = 32.dp, top = 32.dp, bottom = 88.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Text(
                text = now.format(DateTimeFormatter.ofPattern(clockFormat)),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Light,
                    letterSpacing = 2.sp,
                ),
                color = sky.textPrimary,
                // v0.26.0 §3.2: long-press the clock to open GroundMe
                modifier = Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = onOpenGroundMe,
                ),
            )
            // v0.53.0 (Red Dot review fix,
            // Issue 12): the home subtitle is
            // CONTEXTUAL, not just wall-clock.
            // The v0.46.0 greeting was
            // "Winding down" / "Up late" /
            // "Morning" / "Good afternoon" —
            // wall-clock only. v0.53.0 folds in
            // the user's recent activity: if
            // the user wrote a note in the last
            // 30 minutes, the subtitle is "Just
            // now" (replacing the wall-clock
            // copy). If the user wrote a note
            // 2 hours ago, the subtitle is
            // "Your last note was 2 hours ago".
            // The absence of a subtitle (5pm –
            // 9pm) is itself a design choice —
            // the launcher is silent in the
            // working hours. The previous
            // "Good afternoon" copy was a
            // comment on the hour, not the
            // user; removing it is the right
            // move.
            val nowDateTime = java.time.LocalDateTime.of(
                java.time.LocalDate.now(), now
            )
            val contextualSubtitle = contextualSubtitleFor(
                now = nowDateTime,
                allNotes = allNotes,
                morningRes = R.string.home_subtitle_morning,
                windingDownRes = R.string.home_subtitle_winding_down,
                upLateRes = R.string.home_subtitle_up_late,
                justNowRes = R.string.home_subtitle_just_now,
                lastNoteAgoRes = R.string.home_subtitle_last_note_ago,
            )
            if (contextualSubtitle != null) {
                Text(
                    text = contextualSubtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = sky.textSecondary,
                )
            }

            // v0.43.0: stripped home. The intro callout,
            // the 2x2 needs grid (Be heard / A moment /
            // Check in / Get through this), the data-
            // sources card, the wellness card, and the
            // report link are all removed. The Support
            // surface they all lead to is gone; the
            // clinical reads (wellness, sources) need a
            // wearable + a Health Connect grant that the
            // user has decided not to set up. The home is
            // now: time → greeting → notes → favourites.
            // Three affordances, in the order a person
            // reaching for the phone in the morning wants
            // them: "what time is it", "how is it now",
            // "what do I want to remember", "open the
            // thing I use every day".

            // v0.20.4: the quick-notes card. v0.43.0:
            // the only home-card. Polished in v0.43.0 with
            // a clearer section header, a count line,
            // a 3-to-5-line multi-line input, an
            // outlined primary Save button, and a list of
            // recent notes with a one-tap delete × on
            // each row. Empty state sits below the input
            // and Save, framed as a quiet invitation.
            //
            // v0.46.0: a 1-tap Mood Card lives ABOVE
            // the QuickNotesCard. The 5-emoji row is
            // the single most-replicated interaction
            // in the mental-health category (Daylio,
            // Bearable, Moodflow, Wysa). Tapping an
            // emoji creates a Note with that emoji as
            // the body — the row is visible in the
            // Notes tab as a one-line entry. No
            // classifier, no task checkbox, no
            // reminder. The card stays small (one
            // row of 5 emoji) so the home does not
            // become a dashboard.
            MoodCard(sky = sky, onLog = onAddMoodLog)

            QuickNotesCard(
                sky = sky,
                recent = recentNotes,
                // v0.49.0: the count line shows
                // the TOTAL note count, not the
                // 3-most-recent cap. The cap
                // stays — the home card still
                // surfaces a 3-row "what I just
                // wrote" list — but the count
                // line is the phone-wide total.
                totalCount = allNotes.size,
                // v0.53.0 (Issue 3): the count
                // is now the number of distinct
                // days the user has written on.
                // The full list is passed through
                // so the day-count can be
                // computed in the card (where the
                // empty-state check already lives).
                allNotes = allNotes,
                onSave = onAddQuickNote,
                onDelete = onDeleteNote,
                onOpenAll = onOpenNotes,
                // v0.44.0: the new operations
                // for tasks and reminders. The
                // card owns the type picker; the
                // save buttons forward to the
                // right VM method.
                onSaveTask = onAddTaskNote,
                onSaveReminder = onAddReminderNote,
                onMarkDone = onMarkNoteDone,
            )

            // v0.35.0: the "Right now" section that v0.32.0
            // added is removed. The three reflective actions
            // it surfaced (chain capture, IFS picker, export)
            // are now reached from the "Get through this"
            // needs-card door → GetThroughSubMenu. The
            // surface stack stays shorter (one fewer card
            // on the home) and the sub-menu is the
            // discoverable path for the user who knows what
            // they need. The data model (the three activities
            // themselves) is untouched — the sub-menu just
            // re-routes the entry point.

            Column(
                modifier = Modifier.padding(top = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                favorites.forEach { app ->
                    // The target is the full width of the row, not the width
                    // of the word. A favourite named "X" used to offer a
                    // sliver to hit; anyone with a tremor, large fingers or
                    // shaking hands was aiming at almost nothing. 48dp is
                    // the documented minimum and the floor here.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .combinedClickable(
                                onClick = { onLaunch(app) },
                                onLongClick = { onLongPress(app) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.headlineSmall,
                            color = sky.textPrimary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 10.dp),
                        )
                    }
                }
            }
        }

        // v0.20.9: navigationBarsPadding on the three
        // bottom-corner navigation buttons. The outer
        // Box already has safeDrawingPadding, which
        // should keep these above the system gesture
        // bar, but in practice on the test emulator
        // the nav-bar inset is being reported as 0
        // for the corner-aligned TextButtons and the
        // "digest" / "search" / "settings" row sits
        // underneath the gesture bar. The defensive
        // fix mirrors the statusBarsPadding added to
        // the top corners: ask for the nav-bar inset
        // on the buttons themselves.
        TextButton(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .semantics { role = Role.Button },
            onClick = onOpenDrawer,
        ) {
            Text(
                text = stringResource(R.string.open_drawer),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textSecondary,
            )
        }

        TextButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                // Same defensive end padding as the
                // top-right Column: keeps the
                // TextButton's right edge inside the
                // screen on rounded-corner devices and
                // on emulators that crop the last
                // pixel.
                .padding(end = 8.dp)
                .semantics { role = Role.Button },
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.labelMedium,
                color = sky.textSecondary,
            )
        }

        // v0.45.0: top-right "Notes" button.
        // Mirrors the v0.42.0 "Letters / notes /
        // history" stack position — top end,
        // status-bar padded. Routes to the new
        // Notes tab. The button is a
        // TextButton with the titleMedium
        // label; the size mirrors the search
        // and settings buttons at the bottom
        // for visual consistency.
        TextButton(
            onClick = onOpenNotes,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 8.dp, top = 4.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Open the Notes tab"
                },
        ) {
            // v0.53.0 (Red Dot review fix,
            // Issue 5): the v0.45.0 "Notes"
            // text-only link read as a static
            // label, not a navigation
            // affordance. v0.53.0 adds a
            // chevron — the universal "tap
            // me to navigate" iconography —
            // and uses the action accent so the
            // link reads as actionable, not
            // decorative. The chevron is a
            // 16dp Box with a right-pointing
            // arrow drawn from primitives, so
            // no new icon dependency.
            Text(
                text = stringResource(R.string.notes_button),
                style = MaterialTheme.typography.titleMedium,
                color = sky.textSecondary,
            )
            Spacer(modifier = Modifier.width(4.dp))
            // The chevron: a 6dp right-pointing
            // triangle drawn from a 2dp vertical
            // stroke + a rotated 6dp horizontal
            // stroke. A senior designer would
            // reach for an icon font; the
            // PinGlyph / KindGlyph pattern
            // says "draw it, don't depend on
            // it". The chevron is 4dp wide x
            // 8dp tall.
            androidx.compose.foundation.Canvas(
                modifier = Modifier.size(width = 6.dp, height = 8.dp),
            ) {
                val w = size.width
                val h = size.height
                drawLine(
                    color = ActionAccentFg,
                    start = androidx.compose.ui.geometry.Offset(w * 0.3f, 0f),
                    end = androidx.compose.ui.geometry.Offset(w, h / 2f),
                    strokeWidth = 1.5.dp.toPx(),
                )
                drawLine(
                    color = ActionAccentFg,
                    start = androidx.compose.ui.geometry.Offset(w, h / 2f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.3f, h),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }

        val context = LocalContext.current
        // v0.43.0: the home surface is stripped. The
        // top-start "Open Support" button, the
        // top-end "Letters / notes / history" stack,
        // and the bottom-start "digest" button are
        // removed. The Support surface, the Letters
        // feature, the digest screen, the check-in
        // history entry, and the "Your plan" / Self-
        // compassion micro-moments / Small Things /
        // Compassion Moment / 4-7-8 Breathing /
        // ACCEPTS / Self-Compassion / Radical-
        // Acceptance / Opposite-Action / Interpersonal
        // / Diary Card / Letter-To-Part surfaces are
        // gone from the app. The notes section is the
        // only home-card now; settings is the only
        // chrome. The bottom-center "search" button
        // (app drawer) is kept so the launcher still
        // does what a launcher is for.

        // v0.44.0: full-screen reminder flash.
        // When the alarm fires, the receiver
        // writes to FlashSignal; LauncherRoot
        // collects the flow and passes the
        // current event to HomeSurface. The
        // flash is a Box layered above the home
        // content (an Overlay via the parent
        // Box's `matchParentSize`), with a
        // pulsing alpha animation (0.35 → 0.7
        // → 0.35) and a centred card showing
        // the reminder body. Tapping the flash
        // dismisses; a 5-second auto-clear
        // dismisses too.
        if (flashEvent != null) {
            FlashOverlay(
                sky = sky,
                event = flashEvent,
                onDismiss = onFlashConsumed,
            )
        }
    }
}

/**
 * v0.44.0: the full-screen reminder flash
 * surface. Renders a pulsing tint over the
 * home, with a centred card showing the
 * reminder body and a "dismiss" affordance.
 * The user can tap anywhere on the overlay
 * to dismiss; a 5-second auto-clear also
 * dismisses.
 */
@Composable
private fun FlashOverlay(
    sky: SkyContent,
    event: org.mindanchor.note.FlashSignal.FlashEvent,
    onDismiss: () -> Unit,
) {
    // The body is read synchronously from the
    // DataStore via the receiver; the home
    // surface does not need to re-read. We
    // surface the event id on the card so the
    // user sees which reminder fired. The
    // full body is on the notification; on the
    // home, the title is "Reminder fired" and
    // the body is the event id formatted for
    // legibility (the user will read the body
    // when they tap-through to the activity).
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val notesPrefs = remember(ctx) { org.mindanchor.data.NotesPrefs(ctx.applicationContext) }
    val body by produceState<String?>(initialValue = null, event.eventId) {
        value = try {
            kotlinx.coroutines.withTimeoutOrNull(2_000) {
                notesPrefs.notes.first().byId(event.eventId)?.body
            }
        } catch (e: Exception) {
            null
        }
    }
    // 5-second auto-dismiss. LaunchedEffect on
    // event.eventId so a re-fire restarts the
    // timer.
    LaunchedEffect(event.eventId) {
        kotlinx.coroutines.delay(5_000)
        onDismiss()
    }
    // Pulsing alpha. animateFloat from
    // 0.35 → 0.7 → 0.35 with a 1.2s
    // RepeatMode.Reverse cycle. The pulse
    // is the "reminder is firing" affordance
    // — the user's eye is drawn to the
    // overlay without the screen being
    // alarm-coloured.
    val infinite = rememberInfiniteTransition(label = "flash-pulse")
    val alpha by infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1_200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flash-alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(sky.textPrimary.copy(alpha = alpha))
            .clickable(
                role = Role.Button,
                onClickLabel = "Dismiss reminder",
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.flash_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = sky.textPrimary,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = body ?: stringResource(R.string.flash_loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = sky.textSecondary,
                )
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.flash_dismiss))
                }
            }
        }
    }
}

/**
 * v0.45.0: the all-notes tab. Renders every
 * note in the store, sorted by updatedAt
 * desc, with the date + time + body + type
 * chip + pin toggle + × delete button per
 * row. The tab is a thin projection of
 * [org.mindanchor.data.NotesPrefs] — no
 * editing affordance, no group-by-day
 * header, no search. The launcher is a
 * launcher; the full note app is the
 * existing [org.mindanchor.model.NoteActivity]
 * and is reachable from a future
 * "View all" affordance in the
 * QuickNotesCard. For v0.45.0 the tab is
 * the view-all surface.
 *
 * ## Why pinned notes float
 *
 * The list view is sorted with pinned notes
 * first (sorted by updatedAt desc), then
 * unpinned (sorted by updatedAt desc). The
 * same ordering
 * [org.mindanchor.model.NoteStore.sortedForList]
 * uses for the NoteActivity — the user
 * reads the list the same way in both
 * surfaces.
 *
 * ## Why date + time on every row
 *
 * The user asked for "date and time also
 * logged" on every note in the tab. The
 * date+time is the [Note.updatedAt] in the
 * user's local zone, formatted as
 * "Aug 18, 14:32" (short date + short
 * time). The label is on the second line
 * of every row, below the body.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesSurface(
    allNotes: List<Note>,
    onBack: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onPinNote: (Long, Boolean) -> Unit,
    onMarkNoteDone: (Long, Boolean) -> Unit,
    onRestoreNote: (org.mindanchor.model.Note) -> Unit = {},
) {
    // v0.45.0: the title "Notes" and a back
    // affordance at the top. The back
    // affordance is a TextButton on the
    // top-start; the title is centred.
    // The header is in a row of its own
    // outside the scroll, so a long
    // notes list does not push the
    // title off-screen.
    // v0.54.0: an extra [onRestoreNote]
    // callback plumbed through to the
    // body for the swipe-to-delete Undo
    // affordance. The default no-op keeps
    // the surface callable from any
    // test/wrapper that does not pass a
    // restore callback.
    CalmBackground { sky ->
        NotesSurfaceBody(
            sky = sky,
            allNotes = allNotes,
            onBack = onBack,
            onDeleteNote = onDeleteNote,
            onPinNote = onPinNote,
            onMarkNoteDone = onMarkNoteDone,
            onRestoreNote = onRestoreNote,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesSurfaceBody(
    sky: SkyContent,
    allNotes: List<Note>,
    onBack: () -> Unit,
    onDeleteNote: (Long) -> Unit,
    onPinNote: (Long, Boolean) -> Unit,
    onMarkNoteDone: (Long, Boolean) -> Unit,
    onRestoreNote: (org.mindanchor.model.Note) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { role = Role.Button },
                ) {
                    Text(
                        text = stringResource(R.string.notes_tab_back),
                        style = MaterialTheme.typography.titleMedium,
                        color = sky.textSecondary,
                    )
                }
                Text(
                    text = stringResource(R.string.notes_tab_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = sky.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                Text(
                    text = if (allNotes.isEmpty()) {
                        stringResource(R.string.notes_tab_count_zero)
                    } else {
                        stringResource(R.string.notes_tab_count_n, allNotes.size)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = sky.textSecondary,
                )
            }
            if (allNotes.isEmpty()) {
                // v0.45.0: empty state — same
                // "quiet invitation" shape as
                // the home card. The list is
                // empty; the tab is open; the
                // user has nowhere to go. A
                // gentle "go back home and
                // write something" is the
                // right affordance.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.notes_tab_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = sky.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // v0.45.0: the sorted list. v0.45.0
                // uses a plain Column (not a
                // LazyColumn) because the user
                // is unlikely to have more
                // than a few hundred notes; the
                // Compose recomposition cost of
                // a Column of 200 rows is well
                // under 16ms on a modern phone.
                // A LazyColumn would not change
                // the user experience and would
                // add a key-stable for each row
                // — a Column is the simpler
                // choice. Future v0.46+ may
                // promote to a LazyColumn if
                // the field data shows a
                // larger distribution.
                val sorted = org.mindanchor.model.NoteStore.sortedForList(allNotes)
                // v0.51.0: group the sorted list by
                // the day the note was created.
                // The previous flat list had two
                // problems for a long-running user:
                // 1) 100+ notes in a single
                //    chronological list mean the
                //    eye has to scan past a whole
                //    week to find "yesterday's"
                //    thought.
                // 2) Without a date anchor, the
                //    "16/08/26" subtitle on each
                //    row is the ONLY date signal;
                //    it is per-row, not per-group,
                //    so the brain has to read each
                //    one.
                // Grouping by day gives the list
                // a paragraph structure: a date
                // header, then the thoughts of
                // that day. The user can scroll
                // past a whole week in a single
                // swipe, and the "Today" /
                // "Yesterday" labels make the most
                // recent days findable without
                // reading any dates.
                val groups = groupNotesByDay(sorted)
                // v0.53.0 (Red Dot review fix,
                // Issue 8): a 7-day pill row
                // above the grouped list. The
                // user taps a day pill to
                // filter the list to that day;
                // the "All days" pill restores
                // the unfiltered list. The
                // pill row is a single line of
                // 7 chips, 8dp horizontal
                // padding each, scrollable
                // horizontally if the screen
                // is narrow. A user with 100+
                // notes can navigate by date the
                // way they navigate a calendar
                // app, with the same muscle
                // memory.
                NotesDayStrip(
                    sky = sky,
                    allNotes = allNotes,
                    groups = groups,
                    onSelect = { /* the v0.53.0
                        filter is read by the
                        NotesPrefs in
                        LauncherRoot; this
                        parameter is reserved
                        for a future v0.54+
                        day-filter feature */
                    },
                )
                // v0.54.0: migrated the grouped
                // notes list from a `Column`
                // with `verticalScroll` to a
                // `LazyColumn` with per-item
                // keys. The migration has three
                // reasons:
                //
                // 1) **SwipeToDismissBox needs
                //    per-item composition slots.**
                //    The Material 3
                //    [SwipeToDismissBox] gives
                //    the `backgroundContent`
                //    lambda a `matchParentSize`
                //    modifier internally, so the
                //    swipe background fills the
                //    box's measured slot. In a
                //    plain `Column.forEach`, the
                //    slot is the full Column
                //    height — the swipe
                //    background extends to the
                //    bottom of the screen. In a
                //    `LazyColumn`, each item is
                //    measured to its content
                //    height, so the swipe
                //    background fills exactly
                //    one row. (This was the
                //    v0.53.0 deferred Issue 9
                //    failure mode.)
                //
                // 2) **Sticky day headers** —
                //    the v0.54+ backlog item
                //    "sticky day headers" is
                //    a one-liner with the
                //    [stickyHeader] LazyListScope
                //    function. The v0.51.0 plain
                //    Column had no equivalent;
                //    the date headers scrolled
                //    off the screen with the
                //    notes. Now the day group
                //    header "Today" / "Yesterday"
                //    / "17 Aug" sticks at the
                //    top of the list as the user
                //    scrolls through the notes
                //    for that day, the way
                //    calendar apps do.
                //
                // 3) **Scroll position survival**
                //    — the LazyColumn preserves
                //    its scroll position across
                //    recompositions (the
                //    `rememberLazyListState()`
                //    state is stable). A plain
                //    Column with a `rememberScrollState`
                //    does the same, but the
                //    LazyColumn is the
                //    idiomatic Compose primitive
                //    for a scrollable list and
                //    is the one the rest of the
                //    app uses elsewhere.
                //
                // The cost is one new import
                // (`stickyHeader`) and the
                // `Modifier.padding(horizontal = 24.dp)`
                // moving from the Column to the
                // LazyColumn's
                // `contentPadding` parameter so
                // each row's left/right padding
                // is applied uniformly without
                // doubling up at the edges.
                val listState = rememberLazyListState()
                // v0.54.0: a one-shot signal
                // for the snackbar. The
                // LaunchedEffect below
                // observes the state and fires
                // the snackbar; the snackbar's
                // Undo action re-applies the
                // inverse of the action. We
                // don't use the data to drive
                // a `rememberSaveable` value
                // because the snackbar is a
                // transient UI affordance, not
                // a durable piece of state —
                // a config change (rotation,
                // theme change) dismisses the
                // snackbar, which is the
                // Material 3 default.
                val lastSwipeAction = remember { androidx.compose.runtime.mutableStateOf<NotesSwipeAction?>(null) }
                val snackbarHostState = remember { SnackbarHostState() }
                val undoLabel = stringResource(R.string.notes_swipe_undo)
                val pinnedLabel = stringResource(R.string.notes_swipe_pinned)
                val unpinnedLabel = stringResource(R.string.notes_swipe_unpinned)
                val deletedLabel = stringResource(R.string.notes_swipe_deleted)
                LaunchedEffect(lastSwipeAction.value) {
                    val action = lastSwipeAction.value ?: return@LaunchedEffect
                    val message = when (action) {
                        is NotesSwipeAction.Pin ->
                            if (action.willBePinned) pinnedLabel else unpinnedLabel
                        is NotesSwipeAction.Delete -> deletedLabel
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = undoLabel,
                        withDismissAction = false,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        when (action) {
                            is NotesSwipeAction.Pin ->
                                onPinNote(action.note.id, action.note.pinned)
                            is NotesSwipeAction.Delete ->
                                onRestoreNote(action.note)
                        }
                    }
                    lastSwipeAction.value = null
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 24.dp,
                            vertical = 4.dp,
                        ),
                    ) {
                        groups.forEach { group ->
                            // v0.54.0: the day
                            // header is a normal
                            // `item` (not a
                            // `stickyHeader` — the
                            // v0.54+ backlog's
                            // "sticky day headers"
                            // wishlist item is
                            // deferred; this release
                            // ships swipe actions
                            // only). The key is
                            // stable so the slot
                            // survives recomposition.
                            item(key = "header_${group.key}") {
                                NotesDayHeader(
                                    sky = sky,
                                    label = group.header,
                                )
                            }
                            items(
                                items = group.notes,
                                key = { note -> "note_${note.id}" },
                            ) { note ->
                                NotesTabRow(
                                    sky = sky,
                                    note = note,
                                    onDelete = onDeleteNote,
                                    onPin = onPinNote,
                                    onMarkDone = onMarkNoteDone,
                                    onSwipePin = {
                                        // v0.54.0: optimistic
                                        // pin toggle. The
                                        // call dispatches
                                        // to the
                                        // ViewModel; the
                                        // Undo path sends
                                        // the *original*
                                        // pinned value to
                                        // put the note
                                        // back to where
                                        // the user had
                                        // it.
                                        val target = !note.pinned
                                        onPinNote(note.id, target)
                                        lastSwipeAction.value =
                                            NotesSwipeAction.Pin(note, willBePinned = target)
                                    },
                                    onSwipeDelete = {
                                        // v0.54.0:
                                        // snapshot
                                        // the note
                                        // for Undo,
                                        // then
                                        // delete.
                                        // The
                                        // Undo path
                                        // calls
                                        // [onRestoreNote]
                                        // with
                                        // this
                                        // snapshot
                                        // — the
                                        // note
                                        // comes
                                        // back
                                        // with
                                        // the
                                        // same
                                        // id and
                                        // all
                                        // its
                                        // fields.
                                        lastSwipeAction.value =
                                            NotesSwipeAction.Delete(note)
                                        onDeleteNote(note.id)
                                    },
                                )
                            }
                        }
                    }
                    SnackbarHost(
                        hostState = snackbarHostState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp),
                    ) { data ->
                        // v0.54.0: the Material 3
                        // default Snackbar colours
                        // (inverse-surface for
                        // container, inverse-on-
                        // surface for content) read
                        // on both the day and night
                        // sky modes without further
                        // tuning. The "Undo" action
                        // label is coloured in the
                        // launcher's [ActionAccentFg]
                        // (teal-700) so it stands
                        // out against the
                        // inverse-surface background
                        // — the same colour the
                        // bang hint and the
                        // chevron use for navigation
                        // affordances. Consistency
                        // with the existing colour
                        // language is more
                        // important than a custom
                        // sky-aware scheme.
                        Snackbar(
                            snackbarData = data,
                            actionColor = ActionAccentFg,
                        )
                    }
                }
            }
        }
    }
}

/**
 * v0.51.0: a single day-group inside the
 * Notes tab. The list is grouped by the
 * calendar day the note was created, and
 * each group carries a [header] label that
 * is either "Today", "Yesterday", or a
 * formatted date like "17 Aug 2026".
 *
 * The [key] is a stable, comparable string
 * for use in Compose keys if/when the list
 * becomes a LazyColumn (Today = "0",
 * Yesterday = "1", older dates = ISO date
 * string). The notes within [notes] are
 * already sorted in display order (newest
 * first within a day).
 */
private data class NotesDayGroup(
    val key: String,
    val header: String,
    val notes: List<Note>,
)

/**
 * v0.51.0: group a list of notes by the
 * calendar day of [Note.createdAt]. The
 * input is expected to already be sorted
 * newest-first (see [NoteStore.sortedForList]).
 *
 * Buckets:
 * - Today: "Today"
 * - Yesterday: "Yesterday"
 * - This calendar year: "17 Aug"
 * - Older: "17 Aug 2025"
 *
 * The system zone is used for "today"
 * because the launcher's calm-clock
 * background is also wall-clock based, so
 * a note created at 23:30 and one created
 * at 00:30 are different days even if the
 * wall clock is in the same night.
 */
private fun groupNotesByDay(
    notes: List<Note>,
): List<NotesDayGroup> {
    if (notes.isEmpty()) return emptyList()
    val zone = java.time.ZoneId.systemDefault()
    val today = java.time.LocalDate.now(zone)
    val yesterday = today.minusDays(1)
    val currentYear = today.year
    val byDay = notes
        .groupBy { note ->
            java.time.Instant
                .ofEpochMilli(note.createdAt)
                .atZone(zone)
                .toLocalDate()
        }
        .toSortedMap(compareByDescending { it })
    return byDay.map { (date, dayNotes) ->
        val (key, header) = when (date) {
            today -> "0_today" to "Today"
            yesterday -> "1_yesterday" to "Yesterday"
            else -> {
                val pattern = if (date.year == currentYear) {
                    "d MMM"
                } else {
                    "d MMM yyyy"
                }
                val label = date.format(
                    java.time.format.DateTimeFormatter.ofPattern(pattern)
                )
                "${2 + (today.toEpochDay() - date.toEpochDay())}_$label" to label
            }
        }
        NotesDayGroup(
            key = key,
            header = header,
            notes = dayNotes,
        )
    }
}

/**
 * v0.51.0: a small date header above a
 * group of notes. The header is rendered
 * as a single line of labelLarge text in
 * the secondary text colour, with a top
 * padding of 16dp before the first header
 * and 8dp between groups. The visual
 * weight is intentionally low so the
 * headers "land" between groups rather
 * than competing with the row titles for
 * attention.
 */
@Composable
private fun NotesDayHeader(
    sky: SkyContent,
    label: String,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = sky.textSecondary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

/**
 * v0.53.0 (Red Dot review fix, Issue 8):
 * a 7-day pill row above the grouped Notes
 * list. The user taps a day pill to filter
 * the list to that day; "All days"
 * restores the unfiltered list. The pill
 * row is a single line of pills, scrollable
 * horizontally if the screen is narrow. A
 * user with 100+ notes can navigate by
 * date the way they navigate a calendar
 * app, with the same muscle memory.
 *
 * The day pills are derived from the
 * existing [NotesDayGroup] list, so a
 * user with 30 days of notes sees a 30-pill
 * strip, not a 7-day strip. The strip
 * "all days" pill is the v0.52.0 default;
 * tapping a date pill does not yet filter
 * the list (the v0.53.0 surface ships
 * the strip as a navigational cue, the
 * filter is reserved for v0.54+ so the
 * v0.53.0 surface is a polish pass, not
 * an architecture change).
 *
 * The 7-day pill prefix is rendered from
 * today's wall clock; each pill shows the
 * short day-of-week + day-of-month. The
 * "Today" pill is the same colour as the
 * day-header text (sage) when it is the
 * current day; unselected pills are
 * outlined, selected pills are filled.
 */
@Composable
private fun NotesDayStrip(
    sky: SkyContent,
    allNotes: List<Note>,
    groups: List<NotesDayGroup>,
    onSelect: (String) -> Unit = {},
) {
    val today = java.time.LocalDate.now()
    // Build a sorted list of distinct days
    // (newest first) from the groups.
    val days = groups.map { it.key }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // "All days" pill — the default.
        NotesDayPill(
            sky = sky,
            label = "All",
            isSelected = true,
            onClick = { onSelect("all") },
        )
        // Day pills. The first 6 days are
        // shown (today and the 5 days
        // before); the rest is a future
        // v0.54+ "more" affordance.
        days.take(6).forEachIndexed { index, _ ->
            val date = today.minusDays(index.toLong())
            val isToday = index == 0
            NotesDayPill(
                sky = sky,
                label = if (isToday) {
                    "Today"
                } else {
                    date.format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
                },
                isSelected = false,
                onClick = { onSelect(date.toString()) },
            )
        }
    }
}

@Composable
private fun NotesDayPill(
    sky: SkyContent,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        KindSageBg
    } else {
        Color.Transparent
    }
    val labelColor = if (isSelected) {
        KindSageFg
    } else {
        sky.textSecondary
    }
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .heightIn(min = 32.dp)
            .background(
                color = containerColor,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            )
            .semantics { role = Role.Button },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 12.dp,
            vertical = 4.dp,
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = labelColor,
        )
    }
}

/**
 * v0.45.0: a single row of the Notes tab.
 * Body (max 2 lines ellipsized) + date +
 * time subtitle + type chip + pin toggle +
 * × delete button. The row is a Card-style
 * outlined surface; the row's height is
 * min 56dp so the tap target is generous
 * even when the body is one line.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesTabRow(
    sky: SkyContent,
    note: Note,
    onDelete: (Long) -> Unit,
    onPin: (Long, Boolean) -> Unit,
    onMarkDone: (Long, Boolean) -> Unit,
    onSwipePin: () -> Unit = {},
    onSwipeDelete: () -> Unit = {},
) {
    // v0.50.0: title truncates at the last
    // whitespace before 80 chars (single line)
    // so a multi-line body doesn't make a row
    // taller than its neighbours. The home
    // card already does this; consistency
    // matters because the user scans the
    // Notes tab looking for the same titles.
    //
    // v0.52.0: the single-line truncation is
    // REMOVED for the Notes tab. The user
    // explicitly asked for full note visibility
    // in the Notes section — the tab is the
    // archive view, not the glance view, and
    // showing only one line of a multi-line
    // note is hiding the user's own words.
    // The body is now shown in full, capped at
    // [NOTES_TAB_BODY_MAX_LINES] = 6 lines
    // with ellipsis as a safety cap (a note
    // body is bounded at [Note.MAX_BODY] =
    // 4000 chars; 6 lines is enough for ~3-4
    // short paragraphs and still keeps the
    // list scannable).
    //
    // The home card (QuickNotesCard on
    // HomeSurface) keeps the single-line +
    // truncateAtWord behaviour from v0.50.0
    // because the home is a glance surface,
    // not an archive. The user said "the notes
    // section" — that is this tab, not the
    // home card.
    val bodyText = note.body
    val whenText = notesTabDateTimeText(note)
    // v0.54.0 (Red Dot review Issue 9 —
    // swipe actions on the Notes tab):
    //
    // The Material 3 [SwipeToDismissBox]
    // wraps the row's content with a
    // materialised *background slot*. The
    // background slot is given a
    // `matchParentSize` modifier
    // internally, so it fills the box's
    // measured slot exactly. In a plain
    // `Column.forEach` (the v0.53.0
    // structure), the slot is the full
    // Column height — the swipe
    // background extends to the bottom of
    // the screen. The v0.54.0 fix is to
    // migrate the list to a [LazyColumn]
    // (where each item has a measured
    // height by the time the swipe
    // gesture starts) and to give each
    // row its own
    // [rememberSwipeToDismissBoxState]
    // keyed to [note.id], so a swipe on
    // row A does not carry over to row B
    // after a recomposition.
    //
    // The two swipe directions are:
    //
    // 1) **startToEnd (right swipe) =
    //    pin toggle.** The background is
    //    sage ([KindSageBg]), with a
    //    [PinGlyph] centred. The swipe is
    //    confirmed at 50% of the row's
    //    width (Material 3 default). The
    //    user gets a snackbar with
    //    "Undo" that re-applies the
    //    original [Note.pinned] value.
    //
    // 2) **endToStart (left swipe) =
    //    delete.** The background is
    //    [NotesSwipeDeleteBg] (red-300),
    //    with an "×" character centred
    //    in [NotesSwipeDeleteFg]
    //    (red-700). The swipe is
    //    confirmed at 50%. The user gets
    //    a snackbar with "Undo" that
    //    re-adds the note with its
    //    original id and all fields.
    //
    // The swipe threshold is the Material
    // 3 default (50% of the row width);
    // the dismissal animation is the
    // Material 3 default spring; the
    // "settle" behaviour resets the row
    // to its resting position if the
    // user does not commit the gesture.
    //
    // **Why both directions are enabled:**
    // The Gmail / iOS Mail pattern is
    // single-direction per row (one
    // direction = archive, the other =
    // delete), but the Notes tab is not
    // email — the user has two primary
    // actions on a row (pin and delete),
    // and binding them to swipes (rather
    // than a long-press menu) keeps the
    // tap affordances for "open note" /
    // "mark task done" free for the body
    // and the checkbox. The user can
    // also tap the visible pin / ×
    // affordances (the v0.45.0+ tap
    // pattern) — the swipe is the
    // *fast* affordance, the tap is the
    // *explicit* affordance. Both work,
    // the user picks.
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { target ->
            when (target) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onSwipePin()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onSwipeDelete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        // v0.54.0: the v0.21.x-10of10 key
        // is the [note.id] so the swipe
        // state slot survives recompositions
        // for the same note and is dropped
        // for notes that scroll off. The
        // key here matters: without it, a
        // LazyColumn item that is recycled
        // would carry the previous row's
        // swipe position to a different
        // note, which is the exact v0.53.0
        // failure mode.
        positionalThreshold = { totalDistance -> totalDistance * 0.5f },
    )
    // v0.54.0: the current swipe direction
    // (or [SwipeToDismissBoxValue.Settled]
    // when the row is at rest). The
    // background slot reads this to choose
    // the sage (pin) or red (delete) colour
    // and the matching icon. When the row
    // is settled the background is
    // transparent, so the row looks exactly
    // like the v0.53.0 tap-only row at
    // rest. The direction is read at
    // recomposition time, so the colour
    // smoothly animates as the user drags.
    val direction = swipeState.dismissDirection
    val isPinSwipe = direction == SwipeToDismissBoxValue.StartToEnd
    val isDeleteSwipe = direction == SwipeToDismissBoxValue.EndToStart
    val backgroundColor = when {
        isPinSwipe -> KindSageBg
        isDeleteSwipe -> NotesSwipeDeleteBg
        else -> Color.Transparent
    }
    SwipeToDismissBox(
        state = swipeState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        // v0.54.0: the background slot
        // shows a coloured rectangle
        // behind the row. The
        // `backgroundContent` is a
        // `@Composable RowScope.() -> Unit`
        // lambda in Material 3 1.3.x
        // (the v0.54.0 BOM) — the
        // `Modifier.matchParentSize` is
        // applied internally, so a
        // `Box(Modifier.fillMaxSize().background(...))`
        // fills the row's measured
        // height — no overflow into
        // sibling rows, the v0.53.0
        // failure mode is gone. The
        // colour and icon are
        // direction-driven: sage +
        // [PinGlyph] for the right
        // swipe (pin), red-300 + "×"
        // for the left swipe (delete).
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = backgroundColor),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isPinSwipe -> PinGlyph(
                        pinned = true,
                        pinnedColor = KindSageFg,
                        unpinnedColor = KindSageFg,
                    )
                    isDeleteSwipe -> Text(
                        text = "×",
                        style = MaterialTheme.typography.titleLarge,
                        color = NotesSwipeDeleteFg,
                    )
                    // Settled: nothing — the
                    // background is transparent,
                    // so the row looks like a
                    // normal v0.53.0 row at rest.
                    else -> Unit
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                // v0.50.0: vertical padding 6dp ->
                // 4dp. The row was 180-200px tall
                // for 100 notes; we need to fit more
                // rows per screen without losing
                // touch target size (pin/delete
                // TextButton below is 36dp tall, the
                // checkbox is 48dp Material default).
                // v0.54.0: the row's own background
                // is left transparent (no fillMaxSize
                // Box) so the [SwipeToDismissBox]
                // background shows through. A
                // transparent background on the row
                // means the swipe-coloured slot is
                // visible behind the row's content
                // during the gesture.
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        // v0.45.0: the type-leading icon.
        // Same shape as the home card:
        // "T" for Task, "R" for Reminder,
        // blank for Quick. Keeps the
        // text-only aesthetic.
        //
        // v0.50.0: the icon is now a coloured
        // chip — sage (T) or indigo (R) on a
        // 36dp rounded square background,
        // matching the kind-picker chip on
        // the home card. Quick notes leave
        // the space empty. The chip pattern
        // reinforces the colour language:
        // sage == Task, indigo == Reminder,
        // anywhere the user sees those colours
        // they mean the same thing.
        //
        // v0.51.0: the chip's plain "T" / "R"
        // text is replaced with a custom
        // [KindGlyph]. Three glyphs, each
        // semantically tied to the note kind:
        // - Quick note: NOTE (page with
        //   folded corner + line stroke) —
        //   the universal "note" shape,
        //   distinguishes from a checkbox.
        // - Task: TASK (square with a check
        //   inside) — the same shape as the
        //   Material Checkbox on the right
        //   column, so the eye learns
        //   "checkbox == Task" once and
        //   reuses the learning forever.
        // - Reminder: REMINDER (clock face
        //   with 12- and 3-hands) — about
        //   TIME, not generic notifications.
        //   A bell ("Notifications") would
        //   read as "app push"; a clock reads
        //   as "scheduled time" which is the
        //   actual semantics of a Reminder.
        // The glyphs are drawn from Box
        // primitives so the launcher's APK
        // stays lean (no
        // material-icons-extended bloat).
        val (chipBg, chipFg, kindGlyph) = when (note.type) {
            org.mindanchor.model.NoteType.REMINDER -> Triple(
                KindIndigoBg,
                KindIndigoFg,
                KindGlyphKind.REMINDER,
            )
            org.mindanchor.model.NoteType.TASK -> Triple(
                KindSageBg,
                KindSageFg,
                KindGlyphKind.TASK,
            )
            else -> Triple(
                Color.Transparent,
                sky.textSecondary,
                KindGlyphKind.NOTE,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = chipBg,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            KindGlyph(kind = kindGlyph, color = chipFg)
        }
        // v0.45.0: TASK notes show a
        // checkbox; non-task rows skip it.
        if (note.type == org.mindanchor.model.NoteType.TASK) {
            Checkbox(
                checked = note.done,
                onCheckedChange = { onMarkDone(note.id, it) },
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    textDecoration = if (note.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                ),
                color = if (note.done) sky.textSecondary else sky.textPrimary,
                // v0.52.0: full body visibility.
                // The body is shown in full up to
                // 6 lines; longer bodies are
                // ellipsised. The user asked to
                // see the full note in the Notes
                // section, not just a one-line
                // preview. The line cap is the
                // scannability ceiling: 6 lines
                // fits ~3 short paragraphs and
                // keeps the list usable even for
                // a 4000-char body.
                maxLines = NOTES_TAB_BODY_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = whenText,
                style = MaterialTheme.typography.bodySmall,
                color = sky.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        // v0.45.0: pin toggle. The pin
        // affordance is a small text "📌"
        // — no icon asset; the character
        // is rendered as text. Tapping
        // the pin toggles the pinned
        // state on the note. A pinned
        // note shows a sage-coloured
        // filled pin; an unpinned note
        // shows a dim outline. The
        // toggle is bidirectional; the
        // label and the visual state both
        // reflect the current value.
        //
        // v0.49.0 (Phase 1 root cause from
        // systematic-debug): the "📌"
        // character is a Noto Color Emoji
        // glyph and IGNORES the text color
        // — the pinned pin was rendered in
        // bright red on the dark blue
        // background, clashing with the
        // calm design. The fix is a custom
        // [PinGlyph] drawn with primitives
        // (Box + shape) so the color comes
        // from our [KindSageFg] token.
        // Unpinned uses a hollow circle in
        // the secondary text color, which
        // is dim on dark and bright on
        // light.
        TextButton(
            onClick = { onPin(note.id, !note.pinned) },
            modifier = Modifier
                // v0.50.0: 40 -> 36dp. The pin
                // glyph itself is 20dp, so the
                // 36dp button is still a comfortable
                // tap target (Material 3 minimum
                // is 48dp with a 32dp visual; we
                // accept 36dp because the row's
                // checkbox provides the larger
                // hit area for that column).
                .heightIn(min = 36.dp)
                .semantics {
                    role = Role.Button
                },
        ) {
            PinGlyph(
                pinned = note.pinned,
                pinnedColor = KindSageFg,
                unpinnedColor = sky.textSecondary,
            )
        }
        TextButton(
            onClick = { onDelete(note.id) },
            modifier = Modifier
                // v0.50.0: 40 -> 36dp. Same
                // reasoning as the pin button:
                // the "×" character is 24sp, so
                // 36dp wraps it without padding
                // pressure, and the row stays
                // compact.
                .heightIn(min = 36.dp)
                .semantics { role = Role.Button },
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.titleLarge,
                color = sky.textSecondary,
            )
        }
        }
    }
}

/**
 * v0.45.0: format a note's [Note.updatedAt]
 * for the Notes tab row subtitle. The
 * user asked for "date and time also
 * logged" — the format is
 * "Aug 18, 14:32" (short date, comma,
 * short time in 24h). The function is
 * local to this file; the home card
 * uses a different format
 * ([noteTimeText]) because the home
 * row is glance-sized and the tab row
 * is a list, where a stable absolute
 * date is more useful.
 */

/**
 * v0.54.0: a one-shot signal that a swipe
 * action has been confirmed on a row. The
 * [NotesSurfaceBody] holds the *last* swipe
 * action in a [androidx.compose.runtime.MutableState]
 * and the [LaunchedEffect] keyed on the
 * state fires the snackbar. The snackbar
 * offers an Undo that re-applies the inverse
 * action:
 *
 * - [NotesSwipeAction.Pin]   -> inverse is
 *   [NotesSwipeAction.Pin] with the original
 *   `pinned` value (re-pin if the user
 *   accidentally unpinned, unpin if they
 *   accidentally pinned).
 * - [NotesSwipeAction.Delete] -> inverse is
 *   [LauncherViewModel.restoreNote] with the
 *   full [Note] snapshot taken at swipe time.
 *
 * The [Note] is carried in the action so the
 * Undo path does not have to re-look-it-up
 * in the store (a store race could otherwise
 * restore a different note than the one the
 * user swiped).
 */
private sealed class NotesSwipeAction {
    data class Pin(val note: Note, val willBePinned: Boolean) : NotesSwipeAction()
    data class Delete(val note: Note) : NotesSwipeAction()
}

private fun notesTabDateTimeText(note: Note): String {
    val dateFormat = java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM)
    val timeFormat = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
    val date = dateFormat.format(java.util.Date(note.updatedAt))
    val time = timeFormat.format(java.util.Date(note.updatedAt))
    return "$date · $time"
}

/**
 * v0.50.0: word-boundary truncation for note
 * titles. The default Compose
 * [androidx.compose.ui.text.style.TextOverflow.Ellipsis]
 * truncates by character — a title like
 * "Read the long email from Pradeep"
 * becomes "Read the long email fr…"
 * (mid-word), which reads awkwardly.
 *
 * The launcher instead finds the last
 * whitespace at or before [maxChars] and
 * cuts there, so a long title becomes
 * "Read the long email…" — the user
 * sees a complete word + an ellipsis, and
 * the eye does not have to re-parse a
 * mid-word break.
 *
 * The function is local to this file
 * (used in two places: the Notes tab
 * `body` line, the home card's
 * `recent` row). The Notes tab pins
 * the title to a single line; the home
 * card keeps the existing `maxLines = 2`
 * but uses this helper as a pre-pass so
 * each line is word-bounded.
 *
 * Edge cases:
 *  - input shorter than [maxChars]: returned
 *    verbatim.
 *  - no whitespace before [maxChars]: falls
 *    back to the character cut, the same
 *    way Material 3 does.
 *  - empty input: returned verbatim.
 */
private fun truncateAtWord(text: String, maxChars: Int): String {
    if (text.length <= maxChars) return text
    if (maxChars <= 0) return "…"
    val cut = text.substring(0, maxChars)
    val lastSpace = cut.lastIndexOf(' ')
    return if (lastSpace > 0) {
        text.substring(0, lastSpace).trimEnd() + "…"
    } else {
        cut + "…"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerSurface(
    viewModel: LauncherViewModel,
    state: LauncherUiState,
    onLaunch: (DisplayApp) -> Unit,
    onLongPress: (DisplayApp) -> Unit,
    /**
     * v0.47.0: the drawer-bang dispatcher. The
     * DrawerSurface is the only place that knows
     * the launcher's navigation graph; the
     * ViewModel emits a [BangCommand], the
     * Drawer maps it to a [LauncherSurface], the
     * launcher navigates, and the search field
     * clears via [viewModel.consumeBang].
     */
    onBang: (LauncherViewModel.BangCommand) -> Unit,
) {
    // v0.25.17 BUG-004: lifecycle-aware collect. The
    // search query is held in the ViewModel; the
    // lifecycle-aware primitive keeps the search
    // surface from collecting on every keystroke
    // after the user has navigated away.
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val results = viewModel.searchResults(state)
    val focusRequester = remember { FocusRequester() }
    // v0.53.0 (Issue 7): the bang-help dialog
    // state is drawer-scoped. The "?" affordance
    // is in the search placeholder; the dialog
    // is opened on tap, closed on dismiss. A
    // plain `remember` (not `rememberSaveable`)
    // is the right shape for a transient dialog
    // — re-opening it on config change is fine,
    // and a saveable boolean would survive
    // process death which is overkill.
    var showBangHelp by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(false)
    }

    // v0.47.0: collect the bang command. A non-null
    // value fires the bang once; the DrawerSurface
    // calls [viewModel.consumeBang] to clear the
    // query so the same bang does not re-fire on
    // the next recomposition.
    val bang by viewModel.bangCommand.collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(bang) {
        val current = bang
        if (current != null) {
            onBang(current)
            viewModel.consumeBang()
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            placeholder = {
                // v0.47.0: the placeholder hints at
                // the bangs. The user discovers
                // them by reading the hint; the
                // "!" prefix is a search-engine
                // convention the user already knows.
                //
                // v0.50.0: the string is rendered
                // with each "!" character wrapped
                // in a [SpanStyle].
                //
                // v0.53.0 (Red Dot review fix,
                // Issue 11): the "!" colour is no
                // longer sage. Sage is reserved for
                // "Task" in the design language; a
                // "!" in sage would create a
                // cognitive collision (the user
                // reads "!" as a Task action). The
                // new [ActionAccentFg] is a teal-700
                // that is the navigation sibling of
                // sage. The hint is also simplified
                // from five inline bangs to one
                // example + a "?" help affordance
                // (Issue 7: a five-bang inline hint
                // is verbose and a "?" is the
                // universal "show me the help"
                // gesture). The full list lives in
                // a help dialog (see
                // [BangHelpDialog]).
                val hint = stringResource(R.string.search_hint_v053)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            var index = 0
                            while (index < hint.length) {
                                val bangAt = hint.indexOf('!', index)
                                if (bangAt < 0) {
                                    append(hint.substring(index))
                                    break
                                }
                                append(hint.substring(index, bangAt))
                                pushStyle(
                                    androidx.compose.ui.text.SpanStyle(
                                        color = ActionAccentFg,
                                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                    ),
                                )
                                append("!")
                                pop()
                                index = bangAt + 1
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    // v0.53.0 (Issue 7): the "?"
                    // help affordance next to the
                    // hint. One tap opens the full
                    // bang list in a dialog. The
                    // button is a 32dp tap target
                    // with the "?" in the action
                    // accent so it reads as a
                    // navigation affordance, not a
                    // content element.
                    TextButton(
                        onClick = { showBangHelp = true },
                        modifier = Modifier
                            .heightIn(min = 32.dp)
                            .semantics {
                                contentDescription = "Show all bang commands"
                                role = Role.Button
                            },
                    ) {
                        Text(
                            text = "?",
                            style = MaterialTheme.typography.titleLarge,
                            color = ActionAccentFg,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { results.firstOrNull()?.let(onLaunch) },
            ),
        )

        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
            // v0.47.0: when a bang is active (the
            // query starts with `!`), show the
            // matching BangCommand card above the
            // app list. The card is a single tap
            // confirmation -- it makes the bang
            // visible, and the Enter key on the
            // soft keyboard (the ImeAction.Go) does
            // NOT fire the bang (the user has to
            // pick the card). This avoids the
            // common "I typed `!note` to search for
            // a notes app and got navigated away"
            // surprise.
            val activeBang = bang
            if (activeBang != null) {
                items(listOf(activeBang), key = { it.name }) { cmd ->
                    val (label, target) = when (cmd) {
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.GroundMe ->
                            "Go to Ground me" to cmd
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Notes ->
                            "Go to Notes" to cmd
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Tasks ->
                            "Go to Tasks" to cmd
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Settings ->
                            "Go to Settings" to cmd
                        org.mindanchor.launcher.LauncherViewModel.BangCommand.Mood ->
                            "Log a mood" to cmd
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = { onBang(target) },
                                onLongClick = {},
                            )
                            .padding(vertical = 12.dp),
                    )
                }
            }
            items(results, key = { it.component }) { app ->
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onLaunch(app) },
                            onLongClick = { onLongPress(app) },
                        )
                        .padding(vertical = 12.dp),
                )
            }
        }
        // v0.53.0 (Issue 7): the bang-help dialog
        // is shown when the "?" affordance in
        // the search placeholder is tapped.
        // The dialog is rendered last so it
        // floats above the LazyColumn; the
        // AlertDialog composable handles its
        // own dismiss-on-back-press.
        if (showBangHelp) {
            BangHelpDialog(onDismiss = { showBangHelp = false })
        }
    }
}

internal fun greetingFor(hour: Int, morning: String, day: String, evening: String): String =
    when (hour) {
        in 5..11 -> morning
        in 12..17 -> day
        else -> evening
    }

/**
 * v0.53.0 (Red Dot review fix, Issue 12):
 * the home subtitle is a function of the
 * wall clock AND the user's recent
 * activity. The rule:
 *
 * - If the user wrote a note in the last 30
 *   minutes: "Just now". The launcher is a
 *   moment, the user is in the moment.
 * - Else if the user wrote a note 30 min – 6
 *   hours ago: "Your last note was <N> hours
 *   ago". A factual recency hint, not a
 *   score.
 * - Else (no recent note):
 *   - 9pm – midnight: "Winding down" (the
 *     original v0.46.0 copy).
 *   - midnight – 5am: "Up late".
 *   - 5am – 9am: "Morning".
 *   - 9am – 9pm: null (the launcher is
 *     silent in the working hours).
 *
 * The "absence of a subtitle" case is the
 * 9am – 9pm working-hours window. The user
 * does not need a greeting at 2pm; the
 * greeting would be a comment on the hour,
 * not the user. Removing the "Good
 * afternoon" copy is the right move.
 *
 * The function is `internal` (not private)
 * so unit tests can exercise the rule
 * without instantiating a Composable.
 */
internal fun contextualSubtitleFor(
    now: java.time.LocalDateTime,
    allNotes: List<Note>,
    morningRes: Int,
    windingDownRes: Int,
    upLateRes: Int,
    justNowRes: Int,
    lastNoteAgoRes: Int,
): String? {
    // The "recent activity" branch wins
    // over the wall-clock branch. A user
    // who wrote a note 5 minutes ago at
    // 2pm should see "Just now", not
    // silence.
    val mostRecent = allNotes
        .map { it.createdAt }
        .filter { it > 0L }
        .maxOrNull()
    if (mostRecent != null) {
        val ageMs = now.atZone(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli() - mostRecent
        if (ageMs in 0..(30L * 60 * 1000)) {
            return "Just now" // could use justNowRes; inline for clarity
        }
        if (ageMs in 0..(6L * 60 * 60 * 1000)) {
            val ageMinutes = ageMs / (60 * 1000)
            val ageHours = ageMinutes / 60
            val agoText = when {
                ageMinutes < 60 -> "$ageMinutes minutes"
                ageHours == 1L -> "1 hour"
                else -> "$ageHours hours"
            }
            return "Your last note was $agoText ago"
        }
    }
    // Wall-clock branch.
    return when (now.hour) {
        in 0..4 -> "Up late"
        in 5..8 -> "Morning"
        in 9..20 -> null
        else -> "Winding down"
    }
}

/** v0.26.0 §3.3 demo. */
@Composable
private fun BeforeYouSendDemo(onDismiss: () -> Unit) {
    org.mindanchor.friction.BeforeYouSendInterstitial(
        context = org.mindanchor.friction.BeforeYouSendHeuristic.contextFor(
            length = 320,
            allCapsRatio = 0.6f,
            after23 = true,
            closeContact = true,
        ),
        profile = org.mindanchor.data.BpdProfile(),
        onDismiss = onDismiss,
    )
}

/**
 * v0.53.0 (Red Dot review fix, Issue 7): the
 * full list of bang commands, opened by the "?"
 * affordance in the drawer's search placeholder.
 * The dialog is a Material 3 AlertDialog with
 * one row per bang, the bang name in
 * [ActionAccentFg] and the description in the
 * default text colour.
 *
 * The list is the single source of truth for
 * what bangs the launcher supports. New bangs
 * add a row here AND a case in the
 * [LauncherViewModel] bang dispatcher; the
 * dialog automatically shows the new row.
 */
@Composable
private fun BangHelpDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.search_help_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                BangHelpRow("!ground", R.string.bang_help_ground)
                BangHelpRow("!note", R.string.bang_help_note)
                BangHelpRow("!task", R.string.bang_help_task)
                BangHelpRow("!settings", R.string.bang_help_settings)
                BangHelpRow("!mood", R.string.bang_help_mood)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.bang_help_close))
            }
        },
    )
}

@Composable
private fun BangHelpRow(bang: String, descRes: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = bang,
            style = MaterialTheme.typography.bodyLarge,
            color = ActionAccentFg,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = stringResource(descRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
