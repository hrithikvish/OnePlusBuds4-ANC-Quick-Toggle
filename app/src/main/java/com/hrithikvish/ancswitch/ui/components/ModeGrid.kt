package com.hrithikvish.ancswitch.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.BudsProtocol
import com.hrithikvish.ancswitch.ui.theme.AncEase
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncSpring
import com.hrithikvish.ancswitch.ui.theme.AncTheme

/** 3-wide row then a 2-wide row, matching the HTML's `.mode-grid`/`.mode-row2` layout. */
@Composable
fun ModeGrid(
    selectedMode: BudsProtocol.AncMode?,
    enabled: Boolean,
    onSelect: (BudsProtocol.AncMode) -> Unit,
    modifier: Modifier = Modifier,
    modes: List<BudsProtocol.AncMode> = BudsProtocol.AncMode.entries,
) {
    val firstRow = modes.take(3)
    val secondRow = modes.drop(3)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            firstRow.forEach { mode ->
                ModeTile(
                    mode = mode,
                    selected = mode == selectedMode,
                    enabled = enabled,
                    onClick = { onSelect(mode) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (secondRow.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                secondRow.forEach { mode ->
                    ModeTile(
                        mode = mode,
                        selected = mode == selectedMode,
                        enabled = enabled,
                        onClick = { onSelect(mode) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeTile(
    mode: BudsProtocol.AncMode,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AncTheme.colors

    // Dimming for the disabled state is done by blending toward the screen background up front
    // rather than via Modifier.alpha() on the whole tile — a plain alpha wrapper here was
    // observed to fail to paint the background/border at all on at least one real device (an
    // Android 17 preview build), leaving the tile fully transparent instead of dimmed.
    fun dim(color: Color): Color = if (enabled) color else lerp(colors.ink1, color, 0.32f)

    val targetBg = if (selected) colors.paper0 else colors.ink2
    val targetBorder = if (selected) targetBg else colors.line
    val bg by animateColorAsState(dim(targetBg), tween(300, easing = AncEase), label = "tileBg")
    val fg by animateColorAsState(dim(if (selected) colors.ink0 else colors.paper1), tween(300, easing = AncEase), label = "tileFg")
    val borderColor by animateColorAsState(dim(targetBorder), tween(300, easing = AncEase), label = "tileBorder")
    val checkScale by animateFloatAsState(if (selected) 1f else 0.5f, tween(300, easing = AncSpring), label = "checkScale")
    val checkAlpha by animateFloatAsState(if (selected) 1f else 0f, tween(300, easing = AncSpring), label = "checkAlpha")

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(150, easing = AncSpring), label = "tilePress")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(AncShapes.md)
            .background(bg)
            .border(1.dp, borderColor, AncShapes.md)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 9.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(AncIcons.forMode(mode), contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Text(mode.label, style = AncTheme.type.modeLabel, color = fg)
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(15.dp)
                .graphicsLayer { scaleX = checkScale; scaleY = checkScale; alpha = checkAlpha }
                .clip(CircleShape)
                .background(colors.ink0),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AncIcons.Check, contentDescription = null, tint = colors.paper0, modifier = Modifier.size(8.dp))
        }
    }
}
