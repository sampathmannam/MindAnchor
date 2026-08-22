/*
 * v0.39.0: a two-font system. Material 3 defaults to Roboto
 * (or the device sans), which the previous theme softened with
 * weight and letter spacing but did not replace. A Red Dot
 * jury reads the difference between "well-configured Material
 * 3" and "designed" as typography.
 *
 * The split is intentional:
 *   - [CalmSans]  = Inter   (Google Fonts) — humanist,
 *     neutral, designed for UI. Default for every Material 3
 *     typography role, applied globally via [CalmTypography].
 *     The system voice: home shell, settings, navigation,
 *     labels, page titles, section headers.
 *
 *   - [CalmSerif] = Lora    (Google Fonts) — warm, designed
 *     for body text, has a calligraphic warmth without being
 *     literary. Reserved for soft content — the surfaces that
 *     read as intimate or human, not system. Applied per
 *     call site via [SoftContent] and [BreathLabel], never
 *     as a global default.
 *
 * Why two faces and not one: a single font says "this is
 * everywhere." A two-font system says "this is one voice
 * speaking in two registers." MindAnchor already separates
 * system from soft content in colour (dark navy vs warm
 * cream) and density (2x2 needs grid vs single animated
 * breath); the typography now mirrors the same split.
 *
 * "If everything is serif, nothing is." — every editorial
 * designer ever. Serif on the breathing surface, the letter
 * body, the DBT skill titles, the receipt line. Sans on
 * everything else. The two registers only register if both
 * are present and applied with intent.
 */
@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package org.mindanchor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
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

private val InterGoogleFont = GoogleFont("Inter")
private val LoraGoogleFont = GoogleFont("Lora")

/**
 * Inter (humanist sans) for system content. Four weights so
 * the existing weight-light softening in [CalmTypography] can
 * keep its lift without falling back to Roboto when the
 * designer asked for `FontWeight.Light`.
 */
val CalmSans: FontFamily = FontFamily(
    Font(googleFont = InterGoogleFont, fontProvider = provider, weight = FontWeight.Light),
    Font(googleFont = InterGoogleFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = InterGoogleFont, fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = InterGoogleFont, fontProvider = provider, weight = FontWeight.SemiBold),
)

/**
 * Lora (warm serif) for soft content. Two weights — Normal for
 * body, Medium for the DBT skill title case where the action
 * word wants a small lift above the body. Italic is omitted
 * intentionally: MindAnchor does not use italics anywhere
 * (the BDP-safety audit has consistently preferred upright
 * faces — italics can read as "scolding" to a dysregulated
 * reader).
 */
val CalmSerif: FontFamily = FontFamily(
    Font(googleFont = LoraGoogleFont, fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = LoraGoogleFont, fontProvider = provider, weight = FontWeight.Medium),
)

/**
 * System typography — every Material 3 role rendered in
 * [CalmSans]. The same weight, leading, and letter-spacing
 * overrides the v0.21.0 inlined CalmTypography already had,
 * just with the fontFamily swapped from "device default" to
 * Inter. No behavioural change anywhere a Text was already
 * calling `MaterialTheme.typography.<role>`.
 */
internal val DefaultTypography = Typography()

val CalmTypography: Typography = DefaultTypography.copy(
    displayLarge = DefaultTypography.displayLarge.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Light,
        letterSpacing = 2.sp,
    ),
    displayMedium = DefaultTypography.displayMedium.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Light,
    ),
    displaySmall = DefaultTypography.displaySmall.copy(
        fontFamily = CalmSans,
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Normal,
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Normal,
    ),
    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Normal,
        lineHeight = 36.sp,
    ),
    titleLarge = DefaultTypography.titleLarge.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Normal,
    ),
    titleMedium = DefaultTypography.titleMedium.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.4.sp,
    ),
    titleSmall = DefaultTypography.titleSmall.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Normal,
    ),
    bodyLarge = DefaultTypography.bodyLarge.copy(
        fontFamily = CalmSans,
        lineHeight = 26.sp,
    ),
    bodyMedium = DefaultTypography.bodyMedium.copy(
        fontFamily = CalmSans,
        lineHeight = 24.sp,
    ),
    bodySmall = DefaultTypography.bodySmall.copy(
        fontFamily = CalmSans,
        lineHeight = 20.sp,
    ),
    labelLarge = DefaultTypography.labelLarge.copy(
        fontFamily = CalmSans,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = CalmSans,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.6.sp,
    ),
    labelSmall = DefaultTypography.labelSmall.copy(
        fontFamily = CalmSans,
    ),
)

/**
 * The serif voice for soft content. Applied at the call site
 * (Letter body, DBT skill title, receipt entry) — never
 * globally — so the system/soft split is visible to the
 * reader. 17sp body / 1.55 leading is what the DBT handouts
 * in Linehan 1993 use for sustained reading without
 * crowding the column.
 */
val SoftContent: TextStyle = TextStyle(
    fontFamily = CalmSerif,
    fontSize = 17.sp,
    fontWeight = FontWeight.Normal,
    lineHeight = 26.sp,
)

/**
 * The single word on the breathing surface. Lora at 22sp,
 * Light, 4sp letter-spacing — wider than the default so the
 * letters hold their shape against the radial gradient. This
 * is the only label on the surface; if it does not feel
 * intimate, the whole surface reads as a UI screen instead
 * of a place.
 */
val BreathLabel: TextStyle = TextStyle(
    fontFamily = CalmSerif,
    fontSize = 22.sp,
    fontWeight = FontWeight.Light,
    letterSpacing = 4.sp,
)
