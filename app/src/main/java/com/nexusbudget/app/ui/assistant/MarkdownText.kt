package com.nexusbudget.app.ui.assistant

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Renders the small subset of Markdown the assistant uses: headings, bullet and numbered lists,
 * **bold**, *italic* and `code`.
 */
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
    val annotated = remember(text, codeBackground) { parseMarkdown(text, codeBackground) }
    Text(annotated, modifier = modifier, style = MaterialTheme.typography.bodyLarge, color = color)
}

private val bulletRegex = Regex("^\\s*[-*•]\\s+(.*)")
private val numberRegex = Regex("^\\s*(\\d+)[.)]\\s+(.*)")
private val headingRegex = Regex("^#{1,6}\\s+(.*)")

fun parseMarkdown(text: String, codeBackground: Color): AnnotatedString = buildAnnotatedString {
    val lines = text.trim().lines()
    lines.forEachIndexed { index, raw ->
        val bullet = bulletRegex.find(raw)
        val number = numberRegex.find(raw)
        val heading = headingRegex.find(raw)
        when {
            heading != null -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) {
                appendInline(heading.groupValues[1], codeBackground)
            }
            bullet != null -> withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 14.sp))) {
                append("•  ")
                appendInline(bullet.groupValues[1], codeBackground)
            }
            number != null -> withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 0.sp, restLine = 18.sp))) {
                append("${number.groupValues[1]}.  ")
                appendInline(number.groupValues[2], codeBackground)
            }
            else -> appendInline(raw, codeBackground)
        }
        // Paragraph styles add their own line break.
        val isParagraph = bullet != null || number != null
        if (index < lines.lastIndex && !isParagraph) append("\n")
    }
}

private fun AnnotatedString.Builder.appendInline(line: String, codeBackground: Color) {
    var i = 0
    while (i < line.length) {
        when {
            line.startsWith("**", i) -> {
                val end = line.indexOf("**", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { appendInline(line.substring(i + 2, end), codeBackground) }
                    i = end + 2
                } else {
                    append("**")
                    i += 2
                }
            }
            line[i] == '`' -> {
                val end = line.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBackground)) { append(line.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append('`')
                    i++
                }
            }
            (line[i] == '*' || line[i] == '_') && i + 1 < line.length && line[i + 1] != ' ' && (i == 0 || !line[i - 1].isLetterOrDigit()) -> {
                val marker = line[i]
                val end = line.indexOf(marker, i + 1)
                if (end > i + 1 && (end + 1 >= line.length || !line[end + 1].isLetterOrDigit())) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(line.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(marker)
                    i++
                }
            }
            else -> {
                append(line[i])
                i++
            }
        }
    }
}
