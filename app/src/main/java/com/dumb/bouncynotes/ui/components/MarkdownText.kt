package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

// Soporta exactamente el mismo formato de texto que NotallyX: negrita, cursiva,
// tachado, monoespaciado y enlaces clicables (URLs y emails).

private const val LINK_TAG = "URL"
private val linkPattern = Regex("""https?://\S+|www\.\S+|[\w.+-]+@[\w-]+\.[\w.-]+""")

private fun findClosing(text: String, from: Int, marker: String): Int? {
    val idx = text.indexOf(marker, from)
    return if (idx == -1 || idx == from) null else idx
}

fun buildInlineAnnotatedString(text: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    var i = 0
    val n = text.length
    while (i < n) {
        if (text.startsWith("**", i)) {
            val close = findClosing(text, i + 2, "**")
            if (close != null) {
                builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(i + 2, close))
                }
                i = close + 2
                continue
            }
        }
        if (text.startsWith("~~", i)) {
            val close = findClosing(text, i + 2, "~~")
            if (close != null) {
                builder.withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(text.substring(i + 2, close))
                }
                i = close + 2
                continue
            }
        }
        if (text[i] == '`') {
            val close = findClosing(text, i + 1, "`")
            if (close != null) {
                builder.withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color.Gray.copy(alpha = 0.2f)
                    )
                ) {
                    append(text.substring(i + 1, close))
                }
                i = close + 1
                continue
            }
        }
        if (text[i] == '*') {
            val close = findClosing(text, i + 1, "*")
            if (close != null) {
                builder.withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(i + 1, close))
                }
                i = close + 1
                continue
            }
        }
        val linkMatch = linkPattern.matchAt(text, i)
        if (linkMatch != null) {
            val linkText = linkMatch.value
            builder.pushStringAnnotation(LINK_TAG, linkText)
            builder.withStyle(
                SpanStyle(color = Color(0xFF1976D2), textDecoration = TextDecoration.Underline)
            ) {
                append(linkText)
            }
            builder.pop()
            i = linkMatch.range.last + 1
            continue
        }
        builder.append(text[i])
        i++
    }
    return builder.toAnnotatedString()
}

@Composable
fun InlineMarkdownText(text: String) {
    val annotated = remember(text) { buildInlineAnnotatedString(text) }
    val uriHandler = LocalUriHandler.current
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        onClick = { offset ->
            annotated.getStringAnnotations(LINK_TAG, offset, offset).firstOrNull()?.let { ann ->
                val target = when {
                    ann.item.startsWith("http") -> ann.item
                    ann.item.contains("@") -> "mailto:${ann.item}"
                    else -> "https://${ann.item}"
                }
                runCatching { uriHandler.openUri(target) }
            }
        }
    )
}
