package com.hrithikvish.ancswitch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncTheme

@Composable
fun DeviceCard(
    name: String,
    address: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val colors = AncTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(AncShapes.lg)
            .background(colors.ink2)
            .border(1.dp, colors.line, AncShapes.lg)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.ink4),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                AncIcons.Headset,
                contentDescription = null,
                tint = colors.paper0,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = AncTheme.type.deviceName, color = colors.paper0, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(address, style = AncTheme.type.deviceMac, color = colors.paper3)
        }
        trailing()
    }
}
