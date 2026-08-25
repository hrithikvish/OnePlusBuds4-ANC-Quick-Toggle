package com.hrithikvish.ancswitch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncTheme

/** Inverted pill bar with an optional action — the HTML's `.snackbar`. */
@Composable
fun ErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val colors = AncTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AncShapes.pill)
            .background(colors.paper0)
            .padding(start = 16.dp, end = 9.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            message,
            style = AncTheme.type.statusPill,
            color = colors.ink0,
            modifier = Modifier.weight(1f),
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .clip(AncShapes.pill)
                    .background(colors.ink0)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(actionText, style = AncTheme.type.buttonLabel, color = colors.paper0)
            }
        }
    }
}
