package com.dumb.bouncynotes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.pageBodyOf
import com.dumb.bouncynotes.data.pageDateOf
import com.dumb.bouncynotes.data.parseNoteContent
import com.dumb.bouncynotes.data.splitPages
import com.dumb.bouncynotes.p2p.P2pSession
import com.dumb.bouncynotes.ui.components.ChecklistEditor
import com.dumb.bouncynotes.ui.components.InlineMarkdownText
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// Pantallas de "Compartir copia temporal" (P2P por internet, sin servidor; ver
// p2p/P2pSession.kt para cómo funciona la conexión). Los dos lados se intercambian
// a mano dos códigos (por WhatsApp, etc.):
//   quien comparte: genera el código 1 -> lo manda -> pega el código 2 que le devuelven
//   quien recibe:   pega el código 1 -> genera el código 2 -> lo devuelve
// y ahí se conectan. Lo recibido se ve en solo lectura mientras siga la conexión.

// ---------------------------------------------------------------------------
// Quien COMPARTE
// ---------------------------------------------------------------------------

@Composable
fun P2pShareDialog(note: Note, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val session = remember { P2pSession(context) }
    val scope = rememberCoroutineScope()
    val phase by session.phase.collectAsState()
    val status by session.status.collectAsState()
    val progress by session.progress.collectAsState()
    var offerCode by remember { mutableStateOf<String?>(null) }
    var answerText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var connecting by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            offerCode = session.startAsSender(note)
        } catch (e: Exception) {
            error = e.message ?: "No se pudo preparar la conexión"
        }
    }
    // Al cerrar el diálogo se corta la conexión (la copia temporal deja de verse).
    DisposableEffect(Unit) { onDispose { session.close() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compartir copia temporal") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val failure = if (phase == P2pSession.Phase.FAILED) status else error
                val code = offerCode
                when {
                    failure != null -> Text(failure, color = MaterialTheme.colorScheme.error)
                    phase == P2pSession.Phase.CLOSED -> Text("La conexión se cerró.")
                    code == null -> LoadingRow("Preparando el código…")
                    connecting || phase == P2pSession.Phase.CONNECTED -> {
                        Text(status)
                        if (phase == P2pSession.Phase.CONNECTED && progress < 1f) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "La otra persona ve una copia de solo lectura mientras sigan conectados. Al cerrar esto, la conexión se corta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Text("1. Envíale este código a la otra persona:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        CodeBox(code)
                        Spacer(Modifier.height(16.dp))
                        Text("2. Cuando te devuelva SU código, pégalo aquí:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = answerText,
                            onValueChange = { answerText = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("BN1:…") },
                            maxLines = 4
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { answerText = readClipboard(context) }) { Text("Pegar") }
                            Button(
                                enabled = answerText.isNotBlank(),
                                onClick = {
                                    connecting = true
                                    scope.launch {
                                        try {
                                            session.finishAsSender(answerText)
                                        } catch (e: Exception) {
                                            error = e.message ?: "No se pudo conectar"
                                            connecting = false
                                        }
                                    }
                                }
                            ) { Text("Conectar") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

// ---------------------------------------------------------------------------
// Quien RECIBE
// ---------------------------------------------------------------------------

@Composable
fun P2pReceiveFlow(onDismiss: () -> Unit, onSaveCopy: (Note) -> Unit) {
    val context = LocalContext.current
    val session = remember { P2pSession(context) }
    val scope = rememberCoroutineScope()
    val phase by session.phase.collectAsState()
    val status by session.status.collectAsState()
    val progress by session.progress.collectAsState()
    val received by session.received.collectAsState()
    var offerText by remember { mutableStateOf("") }
    var answerCode by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { session.close() } }

    val note = received
    val connectionAlive = phase != P2pSession.Phase.CLOSED && phase != P2pSession.Phase.FAILED
    if (note != null && connectionAlive) {
        SharedNoteViewer(
            note = note,
            saved = saved,
            onSave = {
                val toSave = session.buildNoteToSave()
                if (toSave != null) {
                    onSaveCopy(toSave)
                    saved = true
                    Toast.makeText(context, "Copia guardada", Toast.LENGTH_SHORT).show()
                }
            },
            onClose = onDismiss
        )
        return
    }
    if (note != null) {
        // Se cortó la conexión: la copia temporal ya no está (salvo que se haya guardado).
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Conexión cerrada") },
            text = { Text(if (saved) "La copia que guardaste sigue en tus notas." else "La copia temporal ya no está disponible.") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("Aceptar") } }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Recibir copia temporal") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                val failure = if (phase == P2pSession.Phase.FAILED) status else error
                val reply = answerCode
                if (failure != null) {
                    Text(failure, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                }
                if (reply == null) {
                    Text("1. Pega el código que te mandaron:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = offerText,
                        onValueChange = { offerText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("BN1:…") },
                        maxLines = 4
                    )
                    Spacer(Modifier.height(8.dp))
                    if (busy) {
                        LoadingRow("Preparando la respuesta…")
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { offerText = readClipboard(context) }) { Text("Pegar") }
                            Button(
                                enabled = offerText.isNotBlank(),
                                onClick = {
                                    busy = true
                                    error = null
                                    scope.launch {
                                        try {
                                            answerCode = session.startAsReceiver(offerText)
                                        } catch (e: Exception) {
                                            error = e.message ?: "No se pudo preparar la respuesta"
                                        }
                                        busy = false
                                    }
                                }
                            ) { Text("Continuar") }
                        }
                    }
                } else {
                    Text("2. Devuélvele este código a quien te lo mandó:", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    CodeBox(reply)
                    Spacer(Modifier.height(12.dp))
                    Text(status)
                    if (phase == P2pSession.Phase.CONNECTED && progress < 1f) {
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

// Visor de solo lectura de la copia recibida. Muestra el contenido con el mismo
// motor que el resto de la app (texto con formato, imágenes, checklists), pero
// leyendo los archivos de la carpeta TEMPORAL. No toca la base de datos.
@Composable
private fun SharedNoteViewer(
    note: P2pSession.ReceivedNote,
    saved: Boolean,
    onSave: () -> Unit,
    onClose: () -> Unit
) {
    val shortDate = remember { DateFormat.getDateInstance(DateFormat.SHORT) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Cerrar") }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Copia temporal", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Solo lectura · se cierra al terminar la conexión",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onSave, enabled = !saved) { Text(if (saved) "Guardada ✓" else "Guardar copia") }
                    Spacer(Modifier.width(8.dp))
                }
                val pages = remember(note) {
                    if (note.type == NoteType.JOURNAL) splitPages(note.content) else listOf(note.content)
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (note.title.isNotBlank()) {
                        item { Text(note.title, style = MaterialTheme.typography.headlineSmall) }
                    }
                    if (note.skippedCount > 0) {
                        item {
                            Text(
                                "${note.skippedCount} archivo(s) no se enviaron (muy pesados), así que pueden faltar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    pages.forEachIndexed { pageIndex, raw ->
                        if (pages.size > 1) {
                            item {
                                val date = shortDate.format(Date(pageDateOf(raw) ?: note.createdAt))
                                Text(
                                    "Página ${pageIndex + 1} de ${pages.size} · $date",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        val parts = parseNoteContent(pageBodyOf(raw))
                        parts.forEach { part ->
                            item {
                                when (part) {
                                    is ContentPart.TextPart -> {
                                        if (part.text.isNotBlank()) InlineMarkdownText(part.text.trim('\n'))
                                    }
                                    is ContentPart.ImagePart -> SharedImage(note, part.fileName)
                                    is ContentPart.GalleryPart -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            part.fileNames.forEach { SharedImage(note, it) }
                                        }
                                    }
                                    is ContentPart.VideoPart -> Text(
                                        "🎬 Video (no se muestra en la copia temporal)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    is ContentPart.ChecklistPart -> {
                                        ChecklistEditor(items = part.items, readOnly = true, onItemsChange = {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedImage(note: P2pSession.ReceivedNote, fileName: String) {
    val file = note.files[fileName]
    if (file == null) {
        Text("🖼 Imagen no enviada", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    AsyncImage(
        model = file,
        contentDescription = null,
        contentScale = ContentScale.FillWidth,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
    )
}

// ---------------------------------------------------------------------------
// Ayudas
// ---------------------------------------------------------------------------

@Composable
private fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Spacer(Modifier.width(12.dp))
        Text(text)
    }
}

// Muestra un código (recortado, es largo) con botones para copiarlo o mandarlo por otra app.
@Composable
private fun CodeBox(code: String) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                code,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            copyToClipboard(context, code)
            Toast.makeText(context, "Código copiado", Toast.LENGTH_SHORT).show()
        }) { Text("Copiar") }
        Button(onClick = { shareText(context, code) }) { Text("Compartir") }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("Código de BouncyNotes", text))
}

private fun readClipboard(context: Context): String {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = manager.primaryClip ?: return ""
    if (clip.itemCount == 0) return ""
    return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
}

private fun shareText(context: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(send, "Enviar código").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
