package com.hrithikvish.ancswitch.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncTheme

enum class StatusDotState { NEUTRAL, ON, PULSE, WARN }

@Composable
fun StatusPill(text: String, dotState: StatusDotState, modifier: Modifier = Modifier) {
    val colors = AncTheme.colors
    Row(
        modifier = modifier
            .clip(AncShapes.pill)
            .background(colors.ink2)
            .border(1.dp, colors.line, AncShapes.pill)
            .padding(start = 10.dp, end = 13.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusDot(dotState)
        Text(text, style = AncTheme.type.statusPill, color = colors.paper1)
    }
}

@Composable
private fun StatusDot(state: StatusDotState, modifier: Modifier = Modifier) {
    val colors = AncTheme.colors
    val infinite = rememberInfiniteTransition(label = "statusDot")
    val pulseAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "pulseAlpha",
    )
    val pulseRadius by infinite.animateFloat(
        initialValue = 1f,
        targetValue = 2.3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "pulseRadius",
    )

    Canvas(modifier.size(8.dp)) {
        val dotColor = if (state == StatusDotState.NEUTRAL) colors.paper3 else colors.paper0
        val dotRadius = size.minDimension / 2
        when (state) {
            StatusDotState.ON -> drawCircle(color = dotColor.copy(alpha = 0.16f), radius = dotRadius * 2.4f, center = center)
            StatusDotState.PULSE -> drawCircle(color = dotColor.copy(alpha = pulseAlpha), radius = dotRadius * pulseRadius, center = center)
            StatusDotState.WARN -> drawCircle(
                color = colors.paper3,
                radius = dotRadius + 5.dp.toPx(),
                center = center,
                style = Stroke(width = 1.4.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))),
            )
            StatusDotState.NEUTRAL -> {}
        }
        drawCircle(color = dotColor, radius = dotRadius, center = center)
    }
}
