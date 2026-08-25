package com.hrithikvish.ancswitch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Corner radius tokens from the ancswitch.html `--r-*` custom properties. */
object AncShapes {
    val xl = RoundedCornerShape(30.dp)
    val lg = RoundedCornerShape(22.dp)
    val md = RoundedCornerShape(16.dp)
    val sm = RoundedCornerShape(12.dp)
    val pill = RoundedCornerShape(50)

    /** Sheet-style shape: rounded top corners only, square bottom (flush with screen edge). */
    val sheetTop = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
}
