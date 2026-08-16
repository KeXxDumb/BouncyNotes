package com.example.notes.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@Composable
private fun flatColors() = TextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
    unfocusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
    disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
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

@Composable
fun FlatTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: @Composable (() -> Unit)? = null,
    singleLine: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        placeholder = placeholder,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors = flatColors()
    )
}
