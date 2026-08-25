package com.hrithikvish.ancswitch.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** The two easing curves from ancswitch.html: `--spring` for presses/selection, `--ease` for everything else. */
val AncSpring = CubicBezierEasing(0.3f, 1.2f, 0.4f, 1f)
val AncEase = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
