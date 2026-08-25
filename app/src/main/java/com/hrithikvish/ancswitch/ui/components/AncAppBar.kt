package com.hrithikvish.ancswitch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.R
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncTheme

/** Small wordmark + version chip + eyebrow subtitle atop the connection console. */
@Composable
fun AncAppBar(
    subtitle: String,
    versionLabel: String,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.app_name),
) {
    val colors = AncTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = colors.lineSoft,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = AncTheme.type.bodyStrong, color = colors.paper0)
            Box(
                modifier = Modifier
                    .clip(AncShapes.pill)
                    .background(colors.ink3)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
            ) {
                Text(versionLabel, style = AncTheme.type.eyebrow, color = colors.paper3)
            }
        }
        Text(subtitle, style = AncTheme.type.eyebrow, color = colors.paper3, modifier = Modifier.padding(top = 4.dp))
    }
}
