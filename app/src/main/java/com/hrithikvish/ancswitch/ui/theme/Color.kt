package com.hrithikvish.ancswitch.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The monochrome "ink"/"paper" token set from the ancswitch.html design reference.
 * ink0..ink5 run from page background to strongest surface fill; paper0..paper3
 * run from primary (near-white/near-black) text down to the faintest tertiary text.
 * No hue anywhere by design — state is always carried by shape/inversion, never color.
 */
data class AncPalette(
    val ink0: Color,
    val ink1: Color,
    val ink2: Color,
    val ink3: Color,
    val ink4: Color,
    val ink5: Color,
    val line: Color,
    val lineSoft: Color,
    val paper0: Color,
    val paper1: Color,
    val paper2: Color,
    val paper3: Color,
)

val AncDarkPalette = AncPalette(
    ink0 = Color(0xFF050505),
    ink1 = Color(0xFF101012),
    ink2 = Color(0xFF19191C),
    ink3 = Color(0xFF232326),
    ink4 = Color(0xFF2F2F33),
    ink5 = Color(0xFF454549),
    line = Color(0xFF2C2C30),
    lineSoft = Color(0xFF1F1F22),
    paper0 = Color(0xFFF5F5F2),
    paper1 = Color(0xFFD2D2CF),
    paper2 = Color(0xFF9D9D9C),
    paper3 = Color(0xFF727274),
)

val AncLightPalette = AncPalette(
    ink0 = Color(0xFFF2F2EF),
    ink1 = Color(0xFFFFFFFF),
    ink2 = Color(0xFFEBEBE8),
    ink3 = Color(0xFFE0E0DC),
    ink4 = Color(0xFFD1D1CC),
    ink5 = Color(0xFFA9A9A3),
    line = Color(0xFFD7D7D2),
    lineSoft = Color(0xFFE5E5E1),
    paper0 = Color(0xFF111110),
    paper1 = Color(0xFF3A3A38),
    paper2 = Color(0xFF6C6C68),
    paper3 = Color(0xFF8F8F8A),
)
