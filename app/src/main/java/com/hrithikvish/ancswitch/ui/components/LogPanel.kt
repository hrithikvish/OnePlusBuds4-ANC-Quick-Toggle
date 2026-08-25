package com.hrithikvish.ancswitch.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hrithikvish.ancswitch.R
import com.hrithikvish.ancswitch.ui.theme.AncPalette
import com.hrithikvish.ancswitch.ui.theme.AncShapes
import com.hrithikvish.ancswitch.ui.theme.AncTheme
import kotlinx.coroutines.delay

private val ERROR_MARKERS = listOf("error", "Error", "ECONNRESET", "SOCKET", "Cannot send")
private val HEX_TOKEN = Regex("[0-9A-Fa-f]{2}\\b")

/** Mono log view over ink0, matching the HTML `.log-panel` — RX/TX tags bolded, `-- .. --`
 * lines dimmed, error-ish lines get a left accent bar, and hex-byte runs that decode to
 * printable ASCII get an inline highlighted "quoted" annotation (raw bytes are kept, not
 * replaced — this is still the raw-protocol debug view). */
@Composable
fun LogPanel(
    text: String,
    modifier: Modifier = Modifier,
    showCursor: Boolean = false,
) {
    val colors = AncTheme.colors
    val scrollState = rememberScrollState()
    val lines = remember(text) { text.lines().filter { it.isNotBlank() } }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(AncShapes.md)
            .background(colors.ink0)
            .border(1.dp, colors.line, AncShapes.md)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (lines.isEmpty()) {
            Text(stringResource(R.string.log_idle), style = AncTheme.type.logMono, color = colors.ink5)
        } else {
            lines.forEach { line -> LogLine(line, colors) }
            if (showCursor) BlinkingCursor()
        }
    }
}

@Composable
private fun LogLine(line: String, colors: AncPalette) {
    val trimmed = line.trim()
    val isBracketed = trimmed.startsWith("--") && trimmed.endsWith("--")
    val isError = ERROR_MARKERS.any { trimmed.contains(it) }
    val prefixMatch = Regex("^(RX|TX)\\b").find(trimmed)

    val body: @Composable () -> Unit = {
        when {
            prefixMatch != null -> {
                val tag = prefixMatch.value
                val rest = trimmed.removePrefix(tag)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.paper0, fontWeight = FontWeight.Bold)) { append(tag) }
                        append(highlightAsciiRuns(rest, colors.paper2, colors))
                    },
                    style = AncTheme.type.logMono,
                )
            }
            isBracketed -> Text(trimmed, style = AncTheme.type.logMono, color = colors.ink5)
            else -> Text(highlightAsciiRuns(trimmed, colors.paper2, colors), style = AncTheme.type.logMono)
        }
    }

    if (isError) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            Box(Modifier.width(2.dp).fillMaxHeight().background(colors.paper0))
            Spacer(Modifier.width(8.dp))
            body()
        }
    } else {
        body()
    }
}

private fun highlightAsciiRuns(text: String, baseColor: Color, colors: AncPalette): AnnotatedString =
    buildAnnotatedString {
        val tokens = HEX_TOKEN.findAll(text).toList()
        if (tokens.size < 4) {
            withStyle(SpanStyle(color = baseColor)) { append(text) }
            return@buildAnnotatedString
        }
        var cursor = 0
        var i = 0
        while (i < tokens.size) {
            var j = i
            val bytes = mutableListOf<Int>()
            while (j < tokens.size) {
                val b = tokens[j].value.toInt(16)
                if (b in 0x20..0x7E) {
                    bytes.add(b)
                    j++
                } else {
                    break
                }
            }
            if (bytes.size >= 4) {
                val runEnd = tokens[j - 1].range.last + 1
                withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor, runEnd)) }
                withStyle(SpanStyle(color = colors.paper0, background = colors.ink3)) {
                    append(" \"" + bytes.map { it.toChar() }.joinToString("") + "\"")
                }
                cursor = runEnd
                i = j
            } else {
                i++
            }
        }
        withStyle(SpanStyle(color = baseColor)) { append(text.substring(cursor)) }
    }

@Composable
private fun BlinkingCursor() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            visible = !visible
        }
    }
    Box(
        Modifier
            .padding(top = 2.dp)
            .size(width = 6.dp, height = 11.dp)
            .alpha(if (visible) 1f else 0f)
            .background(AncTheme.colors.paper0),
    )
}
