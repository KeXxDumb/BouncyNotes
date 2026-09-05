package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
private fun flatColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    unfocusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent
)

// Campo de texto sin borde/subrayado: solo un fondo tenue del color de acento.
@Composable
fun FlatTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = singleLine,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = flatColors()
    )
}

// Esta variante (TextFieldValue, la que usa el editor de notas) se armó
// sobre BasicTextField en vez del TextField de Material3 a propósito: M3 no
// expone un callback onTextLayout, y sin eso no hay forma de saber el
// rectángulo EXACTO del cursor dentro del texto — solo el tamaño del campo
// entero. Esa era la causa real de que el "traer a la vista" nunca
// funcionara bien: bringIntoView() sin argumentos trae a la vista TODO el
// composable al que está atado, y en un campo largo (una nota sin imágenes
// es un solo TextField gigante) eso se da por satisfecho con que se vea
// aunque sea un pixel del campo — no necesariamente la línea real donde
// está escribiendo el usuario.
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun FlatTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    // Opcional: si se pasa, el campo pedirá activamente que scrolleen hasta
    // la línea donde está el cursor cada vez que cambia la selección, o
    // cuando el teclado termina de aparecer.
    bringIntoViewRequester: BringIntoViewRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val backgroundAlpha = if (isFocused) 0.22f else 0.07f
    val textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface)
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val requesterModifier = if (bringIntoViewRequester != null) {
        Modifier.bringIntoViewRequester(bringIntoViewRequester)
    } else {
        Modifier
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(requesterModifier)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = backgroundAlpha)),
        textStyle = textStyle,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        onTextLayout = { textLayoutResult = it },
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (value.text.isEmpty() && placeholder != null) {
                    placeholder()
                }
                innerTextField()
            }
        }
    )

    if (bringIntoViewRequester != null) {
        // Reacciona a dos señales, no a una demora fija a ciegas: cuando
        // cambia la selección (se movió el cursor) y cuando el teclado
        // efectivamente termina de aparecer/desaparecer (WindowInsets.isImeVisible).
        // Antes esto era un delay(300) fijo, que en algunos dispositivos
        // resultaba insuficiente (la animación del teclado tarda distinto
        // según el fabricante) y en la práctica terminaba sin scrollear
        // nada. Acá, además, se le pasa el RECTÁNGULO EXACTO del cursor
        // (no el campo entero) — ver el comentario de arriba.
        val imeVisible = WindowInsets.isImeVisible
        LaunchedEffect(value.selection, textLayoutResult, imeVisible) {
            val layout = textLayoutResult ?: return@LaunchedEffect
            val cursorOffset = value.selection.end.coerceIn(0, layout.layoutInput.text.length)
            val cursorRect = layout.getCursorRect(cursorOffset)
            bringIntoViewRequester.bringIntoView(cursorRect)
            // Colchón extra por si el layout de arriba se calculó con el
            // teclado todavía a mitad de animar.
            delay(150)
            bringIntoViewRequester.bringIntoView(cursorRect)
        }
    }
}

// Campo compacto para descripciones de imagen: tipografía chica y una caja que se
// ajusta a esa tipografía. TextField de Material3 no expone su padding interno
// (siempre reserva ~56dp de alto pensados para texto normal), así que usamos
// BasicTextField con nuestro propio padding pequeño para que la caja sea chica
// de verdad, sin recortar el texto.
@Composable
fun CompactCaptionField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface)
    val interactionSource = remember { MutableInteractionSource() }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
        textStyle = textStyle,
        singleLine = true,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Box(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                if (value.isEmpty()) {
                    Text("Descripción", style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                innerTextField()
            }
        }
    )
}
