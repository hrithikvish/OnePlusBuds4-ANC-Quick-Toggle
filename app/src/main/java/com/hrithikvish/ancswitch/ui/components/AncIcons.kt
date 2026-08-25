package com.hrithikvish.ancswitch.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.BudsProtocol

/**
 * Hand-built 24x24 stroke icons, parsed directly from the inline SVG `d` path data in
 * ancswitch.html so the line-art matches the reference exactly. The baked-in stroke color
 * (black) is irrelevant — `Icon(tint = ...)` overrides it via a ColorFilter regardless of
 * what color the vector's own paths declare.
 */
object AncIcons {
    val Headset: ImageVector by lazy {
        strokeIcon(
            "Headset",
            "M4 14a8 8 0 0 1 16 0v5a2 2 0 0 1-2 2h-1a1 1 0 0 1-1-1v-5a1 1 0 0 1 1-1h3" +
                "M4 14v5a2 2 0 0 0 2 2h1a1 1 0 0 0 1-1v-5a1 1 0 0 0-1-1H4",
        )
    }

    val Check: ImageVector by lazy { strokeIcon("Check", "M4 12l5 5L20 6") }

    val ModeOff: ImageVector by lazy {
        strokeIcon(
            "ModeOff",
            "M3,12 a9,9 0 1,0 18,0 a9,9 0 1,0 -18,0",
            "M5.5 5.5l13 13",
        )
    }

    val ModeAncWeak: ImageVector by lazy { strokeIcon("ModeAncWeak", "M5 15V9M10 17V7M14 13V11") }

    val ModeTransparency: ImageVector by lazy {
        strokeIcon("ModeTransparency", "M9 5a7 7 0 0 1 0 14M12.5 8a3.3 3.3 0 0 1 0 8")
    }

    val ModeAncStrong: ImageVector by lazy { strokeIcon("ModeAncStrong", "M4 17V9M9 19V5M14 15V9") }

    val ModeAncAdaptive: ImageVector by lazy {
        strokeIcon("ModeAncAdaptive", "M2 12c1.5-4 3.5-4 5 0s3.5 4 5 0 3.5-4 5-4 3.5 4 5 4")
    }

    val BrokenSignal: ImageVector by lazy {
        strokeIcon("BrokenSignal", "M12 2l3 12M9 9l6 6M9 15l6-6M12 22l3-12")
    }

    /** Material Symbols "bluetooth" glyph (filled) — used for the no-permission empty state. */
    val Bluetooth: ImageVector by lazy {
        filledIcon(
            "Bluetooth",
            "M440-80v-304L256-200l-56-56 224-224-224-224 56-56 184 184v-304h40l228 228-172 172 172 172L480-80h-40Z" +
                "m80-496 76-76-76-74v150Zm0 342 76-74-76-76v150Z",
        )
    }

    /** Shared mode → icon mapping used by both the full mode grid and the quick-tile sheet. */
    fun forMode(mode: BudsProtocol.AncMode): ImageVector = when (mode) {
        BudsProtocol.AncMode.OFF -> ModeOff
        BudsProtocol.AncMode.ANC_WEAK -> ModeAncWeak
        BudsProtocol.AncMode.TRANSPARENCY -> ModeTransparency
        BudsProtocol.AncMode.ANC_STRONG -> ModeAncStrong
        BudsProtocol.AncMode.ANC_ADAPTIVE -> ModeAncAdaptive
    }
}

private fun strokeIcon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/**
 * Material Symbols icons use a `0 -960 960 960` viewBox (origin top-left at y=-960) instead of
 * the `0 0 W H` convention the hand-drawn stroke icons use — wrap the path in a group translated
 * by +960 on y so it lands back in the positive 0..960 space Compose's ImageVector expects.
 */
private fun filledIcon(name: String, pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 960f,
        viewportHeight = 960f,
    ).apply {
        addGroup(translationY = 960f)
        addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = SolidColor(Color.Black),
        )
        clearGroup()
    }.build()
