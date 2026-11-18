package com.dumb.bouncynotes.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.dumb.bouncynotes.data.InlineLayout
import com.dumb.bouncynotes.data.InlinePending
import com.dumb.bouncynotes.data.InlineStyle
import com.dumb.bouncynotes.data.processInlineEdit

// Muestra el texto crudo del editor SIN los marcadores de formato (**, *, ~~,
// `) y con el formato ya aplicado, como un procesador de texto. El texto
// guardado sigue teniendo los marcadores; esto solo cambia lo que se dibuja.
// Los estilos son los mismos que usa la vista de lectura (MarkdownText.kt).
//
// Mapeo de posiciones: de crudo a visible se cuentan los caracteres visibles
// anteriores; de visible a crudo se usa la posición canónica "justo después
// del carácter visible anterior" (ver InlineLayout.rawAfterVisible), que es
// la que mantiene al texto nuevo heredando el formato del carácter anterior.
object InlineMarkupTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val layout = InlineLayout(text.text)
        val styled = buildAnnotatedString {
            append(layout.visible)
            for (span in layout.spans) {
                val start = layout.rawToVisible[span.contentStart]
                val end = layout.rawToVisible[span.contentEnd]
                if (end > start) addStyle(spanStyleFor(span.style), start, end)
            }
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                layout.rawToVisible[offset.coerceIn(0, layout.raw.length)]

            override fun transformedToOriginal(offset: Int): Int =
                layout.rawAfterVisible(offset)
        }
        return TransformedText(styled, mapping)
    }
}

private fun spanStyleFor(style: InlineStyle): SpanStyle = when (style) {
    InlineStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    InlineStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    InlineStyle.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    InlineStyle.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = Color.Gray.copy(alpha = 0.2f))
}

// Aplica el comportamiento "estilo Word" a un cambio del teclado. Devuelve el
// valor que hay que guardar y el estilo armado resultante. Si el resultado
// coincide con lo que produjo el teclado se devuelve ESE mismo valor, sin
// recrearlo, para no cortar la composición del teclado (autocorrección,
// sugerencias) mientras se escribe texto normal.
fun processInlineTextFieldEdit(
    old: TextFieldValue,
    new: TextFieldValue,
    pending: InlinePending?
): Pair<TextFieldValue, InlinePending?> {
    val outcome = processInlineEdit(
        old.text, old.selection.start, old.selection.end,
        new.text, new.selection.start, new.selection.end,
        pending
    )
    val same = outcome.raw == new.text &&
        outcome.selStart == new.selection.start &&
        outcome.selEnd == new.selection.end
    val value = if (same) new else TextFieldValue(outcome.raw, TextRange(outcome.selStart, outcome.selEnd))
    return value to outcome.pending
}
