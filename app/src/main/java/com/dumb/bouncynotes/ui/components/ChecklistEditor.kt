package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dumb.bouncynotes.data.CheckboxPosition
import com.dumb.bouncynotes.data.ChecklistItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    checkboxPosition: CheckboxPosition = CheckboxPosition.START,
    readOnly: Boolean = false,
    // Alto extra (en dp) que hay que dejar libre por debajo de un elemento al
    // autoscrollear hacia él por foco: es el espacio real que tapa la barra
    // inferior flotante y semitransparente (GlassBottomBar). Esa barra a
    // propósito NO reduce el viewport real de scroll (se deja que el
    // contenido se dibuje detrás para poder desenfocarlo — ver
    // NoteEditScreen), así que el "traer a la vista" por defecto no tiene
    // forma de saber que esa franja de pantalla está tapada, y el elemento
    // termina a mitad detrás de la barra en vez de completamente arriba de
    // ella. Se pasa desde NoteEditScreen (mismo valor que usa el Spacer de
    // compensación al final de la lista).
    extraBottomInset: Dp = 0.dp,
    onItemsChange: (List<ChecklistItem>) -> Unit
) {
    // Índice del elemento recién agregado con "Agregar elemento": se usa una
    // sola vez para pedirle el foco apenas entra en composición, y después se
    // limpia. Antes no existía nada de esto, así que agregar un elemento lo
    // dejaba en la lista sin el teclado abierto ni el cursor puesto — había
    // que tocarlo a mano para empezar a escribir.
    var pendingFocusIndex by remember { mutableStateOf<Int?>(null) }
    val extraBottomInsetPx = with(LocalDensity.current) { extraBottomInset.toPx() }
    val coroutineScope = rememberCoroutineScope()

    Column {
        items.forEachIndexed { index, item ->
            key(index) {
                // Divisor sutil entre elementos (no antes del primero).
                if (index > 0) {
                    ChecklistItemDivider()
                }

                val focusRequester = remember(index) { FocusRequester() }
                val bringIntoViewRequester = remember(index) { BringIntoViewRequester() }
                // Tamaño real de la fila en píxeles, capturado por layout.
                // Se necesita para poder inflar el rectángulo que se le pide
                // a bringIntoView() (ver onFocusChanged más abajo): sin el
                // tamaño real no hay forma de saber dónde termina el propio
                // elemento antes de sumarle el colchón de la barra inferior.
                var rowWidthPx by remember(index) { mutableStateOf(0f) }
                var rowHeightPx by remember(index) { mutableStateOf(0f) }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            rowWidthPx = coordinates.size.width.toFloat()
                            rowHeightPx = coordinates.size.height.toFloat()
                        }
                        .bringIntoViewRequester(bringIntoViewRequester),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val checkbox: @Composable () -> Unit = {
                        Checkbox(
                            checked = item.checked,
                            onCheckedChange = { checked ->
                                onItemsChange(items.toMutableList().also { it[index] = item.copy(checked = checked) })
                            }
                        )
                    }
                    val textArea: @Composable () -> Unit = {
                        if (readOnly) {
                            Text(
                                text = item.text,
                                textDecoration = if (item.checked) TextDecoration.LineThrough else null,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            FlatTextField(
                                value = item.text,
                                onValueChange = { text ->
                                    onItemsChange(items.toMutableList().also { it[index] = item.copy(text = text) })
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { focusState ->
                                        // Al tomar foco (por toque del usuario o por el
                                        // auto-foco de un elemento recién agregado), se
                                        // pide traer a la vista la fila COMPLETA más el
                                        // colchón de la barra inferior — no el campo
                                        // entero de M3 por sí solo, que no sabe nada de
                                        // esa barra.
                                        if (focusState.isFocused) {
                                            val targetRect = Rect(
                                                left = 0f,
                                                top = 0f,
                                                right = rowWidthPx,
                                                bottom = rowHeightPx + extraBottomInsetPx
                                            )
                                            coroutineScope.launch {
                                                bringIntoViewRequester.bringIntoView(targetRect)
                                            }
                                        }
                                    },
                                placeholder = { Text("Elemento") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                            )
                        }
                    }

                    if (checkboxPosition == CheckboxPosition.START) {
                        checkbox()
                        textArea()
                    } else {
                        textArea()
                        checkbox()
                    }

                    if (!readOnly) {
                        IconButton(onClick = {
                            onItemsChange(items.toMutableList().also { it.removeAt(index) })
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Quitar")
                        }
                    }
                }

                if (!readOnly && index == pendingFocusIndex) {
                    // Corre una sola vez, justo cuando ESTE índice entra en
                    // composición por primera vez (recién agregado).
                    LaunchedEffect(Unit) {
                        focusRequester.requestFocus()
                        pendingFocusIndex = null
                    }
                }
            }
        }
        if (!readOnly) {
            TextButton(onClick = {
                val newIndex = items.size
                onItemsChange(items + ChecklistItem())
                pendingFocusIndex = newIndex
            }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Agregar elemento")
            }
        }
    }
}

// Divisor sutil entre elementos del checklist: mismo criterio ya usado en el
// resto de la app (color de acento a baja opacidad, en vez del gris por
// defecto de HorizontalDivider) para mantener paridad visual.
@Composable
private fun ChecklistItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
    )
}
