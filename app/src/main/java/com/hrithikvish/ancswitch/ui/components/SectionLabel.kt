package com.hrithikvish.ancswitch.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hrithikvish.ancswitch.ui.theme.AncTheme

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = AncTheme.type.sectionLabel,
        color = AncTheme.colors.paper3,
        modifier = modifier,
    )
}
