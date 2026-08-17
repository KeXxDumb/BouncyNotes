package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.dumb.bouncynotes.data.CheckboxPosition
import com.dumb.bouncynotes.data.ChecklistItem

@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    checkboxPosition: CheckboxPosition = CheckboxPosition.START,
    readOnly: Boolean = false,
    onItemsChange: (List<ChecklistItem>) -> Unit
) {
    Column {
        items.forEachIndexed { index, item ->
            key(index) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                                modifier = Modifier.weight(1f),
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
            }
        }
        if (!readOnly) {
            TextButton(onClick = { onItemsChange(items + ChecklistItem()) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Agregar elemento")
            }
        }
    }
}
