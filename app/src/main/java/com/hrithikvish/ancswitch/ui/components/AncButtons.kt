package com.hrithikvish.ancswitch.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncSpring
import com.hrithikvish.ancswitch.ui.theme.AncTheme

@Composable
fun AncPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    small: Boolean = false,
) {
    AncButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        dimWhenDisabled = !loading,
        containerColor = AncTheme.colors.paper0,
        borderColor = null,
        small = small,
    ) {
        if (loading) {
            AncButtonSpinner(color = AncTheme.colors.ink0)
        } else {
            Text(text, style = AncTheme.type.buttonLabel, color = AncTheme.colors.ink0)
        }
    }
}

@Composable
fun AncGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    small: Boolean = false,
    emphasized: Boolean = false,
) {
    val colors = AncTheme.colors
    val contentColor = if (emphasized) colors.paper0 else colors.paper1
    AncButtonBase(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled && !loading,
        dimWhenDisabled = !loading,
        containerColor = if (emphasized) colors.ink4 else colors.ink3,
        borderColor = colors.line,
        small = small,
    ) {
        if (loading) {
            AncButtonSpinner(color = contentColor)
        } else {
            Text(text, style = AncTheme.type.buttonLabel, color = contentColor)
        }
    }
}

@Composable
private fun AncButtonSpinner(color: Color, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "btnSpinner")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 700, easing = LinearEasing)),
        label = "btnSpinnerRotation",
    )
    Canvas(modifier.size(14.dp)) {
        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 270f,
                useCenter = false,
                style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun AncButtonBase(
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    dimWhenDisabled: Boolean,
    containerColor: Color,
    borderColor: Color?,
    small: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(durationMillis = 150, easing = AncSpring),
        label = "buttonPressScale",
    )

    var shapeModifier = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clip(AncShapes.pill)
        .background(containerColor)
    if (borderColor != null) {
        shapeModifier = shapeModifier.border(1.dp, borderColor, AncShapes.pill)
    }
    Row(
        modifier = shapeModifier
            .alpha(if (!enabled && dimWhenDisabled) 0.35f else 1f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(
                PaddingValues(
                    horizontal = if (small) 14.dp else 18.dp,
                    vertical = if (small) 8.dp else 11.dp,
                )
            ),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
