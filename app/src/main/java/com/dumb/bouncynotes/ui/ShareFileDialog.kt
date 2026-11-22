package com.dumb.bouncynotes.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.ShareFileManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareFileDialog(
    notes: List<Note>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var useExpiry by remember { mutableStateOf(false) }
    var expiresAt by remember { mutableStateOf<Long?>(null) }
    var pickedDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val datePickerState = rememberDatePickerState()
    val timePickerState = rememberTimePickerState()

    val dateText = expiresAt?.let {
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "Sin fecha límite"

    // Se usa AlertDialog + DatePicker/TimePicker en vez de DatePickerDialog /
    // TimePickerDialog: la firma de esos últimos cambió entre versiones de
    // Material 3 (algunas no tienen parámetro `title`), y este proyecto está
    // fijado a una versión concreta. AlertDialog es estable.
    if (showDatePicker) {
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("Elegir fecha") },
            text = { DatePicker(state = datePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    pickedDate = datePickerState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        )
    }

    if (showTimePicker) {
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Elegir hora") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = pickedDate ?: System.currentTimeMillis()
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    expiresAt = cal.timeInMillis
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir archivo .bnotes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Se guardarán ${notes.size} nota(s) en un archivo cifrado " +
                        "que se puede compartir y abrir con BouncyNotes."
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Poner fecha límite", modifier = Modifier.fillMaxWidth(0.75f))
                    Switch(
                        checked = useExpiry,
                        onCheckedChange = { enabled ->
                            useExpiry = enabled
                            if (!enabled) expiresAt = null
                        }
                    )
                }
                if (useExpiry) {
                    Button(onClick = { showDatePicker = true }) {
                        Text(if (expiresAt == null) "Elegir fecha y hora" else "Cambiar: $dateText")
                    }
                    Text(
                        "Al llegar esa hora las notas se eliminan y el archivo deja de abrirse.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (useExpiry && expiresAt == null) {
                    error = "Elegí la fecha y hora límite."
                    return@TextButton
                }
                val file = ShareFileManager.createFile(
                    context = context,
                    notes = notes,
                    expiresAt = if (useExpiry) expiresAt else null
                )
                if (file == null) {
                    error = "No se pudo crear el archivo."
                    return@TextButton
                }
                val uri = ShareFileManager.uriFor(context, file)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = ShareFileManager.MIME_TYPE
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Notas de BouncyNotes")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(sendIntent, "Compartir notas"))
                }
                onDismiss()
            }) { Text("Crear y compartir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
