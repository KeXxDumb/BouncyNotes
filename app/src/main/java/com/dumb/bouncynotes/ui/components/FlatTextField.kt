package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FlatTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    // Opcional: si se pasa, el campo pedirá activamente que lo "traigan a la
    // vista" (scrolleen) cada vez que cambia la selección/cursor. Sin esto, en un
    // Column con verticalScroll un campo de texto multilínea que crece (por
    // ejemplo al presionar Enter) no siempre queda visible: Compose no vuelve a
    // pedir el scroll automáticamente en cada salto de línea dentro de un campo
    // ya enfocado, así que el cursor podía terminar tapado por el teclado o fuera
    // de la pantalla y había que deslizar a mano para volver a verlo.
    bringIntoViewRequester: BringIntoViewRequester? = null
) {
    val requesterModifier = if (bringIntoViewRequester != null) {
        Modifier.bringIntoViewRequester(bringIntoViewRequester)
    } else {
        Modifier
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.then(requesterModifier),
        placeholder = placeholder,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = flatColors()
    )
    if (bringIntoViewRequester != null) {
        LaunchedEffect(value.selection) {
            bringIntoViewRequester.bringIntoView()
            // El teclado tarda unos cientos de ms en terminar de animar y
            // reportar su alto final (WindowInsets.ime). Si este efecto se
            // dispara justo cuando el campo recibe foco por primera vez —
            // el caso más común de "salto de línea"/toque en una nota —
            // ese primer bringIntoView() puede calcular el scroll con un
            // viewport que todavía no descontó el teclado completo, y
            // quedarse corto. Un segundo pedido, un toque después, corrige
            // eso sin afectar el caso normal (que ya queda bien resuelto
            // con el primero).
            delay(300)
            bringIntoViewRequester.bringIntoView()
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
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
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
