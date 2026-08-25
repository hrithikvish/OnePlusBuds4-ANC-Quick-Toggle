package com.hrithikvish.ancswitch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncTheme

/** Icon-in-squircle + title + body + optional action slot — the no-permission / empty-list state. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = AncTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(AncShapes.lg)
                .background(colors.ink3)
                .border(1.dp, colors.line, AncShapes.lg),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.paper1, modifier = Modifier.size(25.dp))
        }
        Text(title, style = AncTheme.type.bodyStrong, color = colors.paper0, textAlign = TextAlign.Center)
        Text(
            body,
            style = AncTheme.type.body,
            color = colors.paper2,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 230.dp),
        )
        if (action != null) {
            Spacer(Modifier.height(2.dp))
            action()
        }
    }
}

/** Dashed-border placeholder — the HTML's `.skeleton-card` visual, repurposed here for a real
 * empty state (no bonded devices) rather than a fake loading shimmer. */
@Composable
fun DashedHintCard(text: String, modifier: Modifier = Modifier) {
    val colors = AncTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .dashedBorder(colors.line, 22.dp)
            .padding(18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AncTheme.type.body, color = colors.paper3, textAlign = TextAlign.Center)
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.dp): Modifier =
    this.drawWithContent {
        drawContent()
        val inset = strokeWidth.toPx() / 2
        drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2),
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f))),
        )
    }
