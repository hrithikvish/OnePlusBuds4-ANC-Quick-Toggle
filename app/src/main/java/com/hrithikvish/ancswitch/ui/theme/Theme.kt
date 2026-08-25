package com.hrithikvish.ancswitch.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf

val LocalAncColors = staticCompositionLocalOf { AncDarkPalette }
val LocalAncTextStyles = staticCompositionLocalOf { AncTextStylesInstance }

/** `MaterialTheme`-style accessor: `AncTheme.colors.paper0`, `AncTheme.type.h1`, `AncTheme.shapes.lg`. */
object AncTheme {
    val colors: AncPalette
        @Composable get() = LocalAncColors.current

    val type: AncTextStyles
        @Composable get() = LocalAncTextStyles.current

    val shapes: AncShapes = AncShapes
}

/**
 * Monochrome design system from ancswitch.html. Deliberately has no `dynamicColor` toggle —
 * the "selection = inversion" interaction language only works because nothing else on screen
 * carries color, so wallpaper-derived Material You color is not an option here.
 */
@Composable
fun ANCSwitchTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) AncDarkPalette else AncLightPalette

    val materialColorScheme = if (darkTheme) {
        darkColorScheme(
            background = palette.ink0,
            surface = palette.ink1,
            surfaceVariant = palette.ink2,
            primary = palette.paper0,
            onPrimary = palette.ink0,
            secondary = palette.paper1,
            onSecondary = palette.ink0,
            onBackground = palette.paper0,
            onSurface = palette.paper0,
            onSurfaceVariant = palette.paper1,
            outline = palette.line,
            outlineVariant = palette.lineSoft,
            error = palette.paper0,
            onError = palette.ink0,
            errorContainer = palette.ink3,
            onErrorContainer = palette.paper0,
        )
    } else {
        lightColorScheme(
            background = palette.ink0,
            surface = palette.ink1,
            surfaceVariant = palette.ink2,
            primary = palette.paper0,
            onPrimary = palette.ink0,
            secondary = palette.paper1,
            onSecondary = palette.ink0,
            onBackground = palette.paper0,
            onSurface = palette.paper0,
            onSurfaceVariant = palette.paper1,
            outline = palette.line,
            outlineVariant = palette.lineSoft,
            error = palette.paper0,
            onError = palette.ink0,
            errorContainer = palette.ink3,
            onErrorContainer = palette.paper0,
        )
    }

    CompositionLocalProvider(
        LocalAncColors provides palette,
        LocalAncTextStyles provides AncTextStylesInstance,
    ) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = AncMaterialTypography,
            content = content,
        )
    }
}
