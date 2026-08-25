package com.hrithikvish.ancswitch.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hrithikvish.ancswitch.R

/**
 * Both fonts are bundled as their upstream variable-font .ttf (from the google/fonts OFL repo)
 * under res/font/ — a single file per family, with each weight below just a different
 * `wght` axis setting on that one file. No network/provider dependency, no cert boilerplate.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Display family — headings, labels, buttons. */
val AncDisplayFontFamily = FontFamily(
    variableFont(R.font.plus_jakarta_sans, FontWeight.Normal),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Medium),
    variableFont(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    variableFont(R.font.plus_jakarta_sans, FontWeight.Bold),
    variableFont(R.font.plus_jakarta_sans, FontWeight.ExtraBold),
)

/** Mono family — log panel, hex/UUID/mac text, eyebrow labels. */
val AncMonoFontFamily = FontFamily(
    variableFont(R.font.jetbrains_mono, FontWeight.Normal),
    variableFont(R.font.jetbrains_mono, FontWeight.Medium),
    variableFont(R.font.jetbrains_mono, FontWeight.SemiBold),
    variableFont(R.font.jetbrains_mono, FontWeight.Bold),
)

/**
 * Named text styles mirroring the distinct CSS classes in ancswitch.html, so component code
 * never hand-rolls fontSize/letterSpacing — each treatment is defined once, here.
 */
data class AncTextStyles(
    val h1: TextStyle,
    val sub: TextStyle,
    val eyebrow: TextStyle,
    val sectionLabel: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val buttonLabel: TextStyle,
    val modeLabel: TextStyle,
    val statusPill: TextStyle,
    val logMono: TextStyle,
    val deviceName: TextStyle,
    val deviceMac: TextStyle,
)

val AncTextStylesInstance = buildAncTextStyles()

private fun buildAncTextStyles() = AncTextStyles(
    h1 = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
    ),
    sub = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 21.sp,
    ),
    eyebrow = TextStyle(
        fontFamily = AncMonoFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 1.4.sp,
    ),
    sectionLabel = TextStyle(
        fontFamily = AncMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        letterSpacing = 1.2.sp,
    ),
    body = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    ),
    bodyStrong = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = (-0.2).sp,
    ),
    buttonLabel = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
    ),
    modeLabel = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
    ),
    statusPill = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.5.sp,
    ),
    logMono = TextStyle(
        fontFamily = AncMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
        lineHeight = 16.sp,
    ),
    deviceName = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
    ),
    deviceMac = TextStyle(
        fontFamily = AncMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.5.sp,
    ),
)

/** Baseline Material3 typography (display family) for any stock component we didn't restyle ourselves. */
val AncMaterialTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = AncDisplayFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
)
