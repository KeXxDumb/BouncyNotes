package com.example.notes.ui.components

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.notes.data.ChecklistItem

@Composable
fun ChecklistEditor(
    items: List<ChecklistItem>,
    onItemsChange: (List<ChecklistItem>) -> Unit
) {
    Column {
        items.forEachIndexed { index, item ->
            key(index) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.checked,
                        onCheckedChange = { checked ->
                            onItemsChange(items.toMutableList().also { it[index] = item.copy(checked = checked) })
                        }
                    )
                    OutlinedTextField(
                        value = item.text,
                        onValueChange = { text ->
                            onItemsChange(items.toMutableList().also { it[index] = item.copy(text = text) })
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Elemento") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    IconButton(onClick = {
                        onItemsChange(items.toMutableList().also { it.removeAt(index) })
                    }) {
                        Icon(Icons.Filled.Close, contentDescription = "Quitar")
                    }
                }
            }
        }
        TextButton(onClick = { onItemsChange(items + ChecklistItem()) }) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Agregar elemento")
        }
    }
}
