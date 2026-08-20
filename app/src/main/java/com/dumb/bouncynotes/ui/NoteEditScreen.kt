package com.dumb.bouncynotes.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.outlined.AlarmAdd
import androidx.compose.material.icons.filled.AlarmOff
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.withTimeoutOrNull
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteImage
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.buildImageTag
import com.dumb.bouncynotes.data.extractImageFileNames
import com.dumb.bouncynotes.data.isNoteEmpty
import com.dumb.bouncynotes.data.parseNoteContent
import com.dumb.bouncynotes.data.removeImageOccurrence
import com.dumb.bouncynotes.ui.components.ChecklistEditor
import com.dumb.bouncynotes.ui.components.CompactCaptionField
import com.dumb.bouncynotes.ui.components.FlatTextField
import com.dumb.bouncynotes.ui.components.NoteContentView
import com.dumb.bouncynotes.ui.components.RgbColorPicker
import java.io.File

// Modelo local para el editor segmentado: cada tramo de texto es un campo editable
// y cada imagen se renderiza de verdad (no como etiqueta de texto crudo).
private sealed class EditSegment {
    data class TextSeg(val value: TextFieldValue) : EditSegment()
    data class ImageSeg(val fileName: String, val caption: String) : EditSegment()
}

private fun buildEditSegments(content: String): List<EditSegment> {
    val parts = parseNoteContent(content)
    if (parts.isEmpty()) return listOf(EditSegment.TextSeg(TextFieldValue("")))
    // Si dos imágenes quedan una justo al lado de la otra en el contenido guardado
    // (sin ningún carácter entre medio), parseNoteContent no genera ningún TextPart
    // ahí porque literalmente no hay texto que representar. Eso hacía que, al volver
    // a abrir la nota, no existiera ningún campo editable entre esas imágenes y por
    // lo tanto fuera imposible escribir ahí. Insertamos un tramo de texto vacío
    // (solo en memoria, para editar) entre imágenes consecutivas, y al principio/final
    // si la nota empieza o termina con una imagen.
    val segments = mutableListOf<EditSegment>()
    parts.forEach { part ->
        when (part) {
            is ContentPart.TextPart -> segments.add(EditSegment.TextSeg(TextFieldValue(part.text)))
            is ContentPart.ImagePart -> {
                if (segments.isEmpty() || segments.last() is EditSegment.ImageSeg) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.ImageSeg(part.fileName, part.caption))
            }
        }
    }
    if (segments.last() is EditSegment.ImageSeg) {
        segments.add(EditSegment.TextSeg(TextFieldValue("")))
    }
    return segments
}

// El doble-tap para pasar a modo edición se hacía con detectTapGestures en el
// contenedor padre, en el pass por defecto (Main). Ese pass viaja de hijos hacia
// el padre, así que cuando el toque caía sobre un elemento hijo con su propia
// detección de toques (los enlaces del texto vía ClickableText, o antes las
// imágenes con .clickable), el hijo consumía el evento primero y el padre nunca
// llegaba a detectar el doble toque: por eso solo funcionaba tocando "fuera" del
// texto/imagen. Usando el pass Initial (que viaja de padre a hijos, antes de que
// cualquier hijo pueda consumir el evento) el contenedor ve el toque siempre.
private suspend fun PointerInputScope.detectDoubleTapToEdit(onDoubleTap: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(pass = PointerEventPass.Initial)
        } ?: return@awaitEachGesture
        secondDown.consume()
        waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
        onDoubleTap()
    }
}

private fun segmentsToContent(segments: List<EditSegment>): String =
    segments.joinToString("") { seg ->
        when (seg) {
            is EditSegment.TextSeg -> seg.value.text
            is EditSegment.ImageSeg -> buildImageTag(seg.fileName, seg.caption)
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    initialType: NoteType,
    viewModel: NoteViewModel,
    settings: AppSettings,
    biometricUnlockedForPrivate: Boolean,
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit,
    allLabels: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var loaded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(Note(type = initialType)) }
    var segments by remember { mutableStateOf(listOf<EditSegment>(EditSegment.TextSeg(TextFieldValue("")))) }
    var activeSegmentIndex by remember { mutableStateOf(0) }
    var unlockedThisNote by remember { mutableStateOf(false) }
    var pendingCameraFileName by remember { mutableStateOf<String?>(null) }
    var viewerStartPos by remember { mutableStateOf<Int?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showReminderSheet by remember { mutableStateOf(false) }
    var captionActiveIndices by remember { mutableStateOf(setOf<Int>()) }
    var isEditing by remember { mutableStateOf(true) }

    // Para que el recordatorio realmente se vea, en Android 13+ hace falta el
    // permiso de notificaciones. Se pide justo al programar el primer
    // recordatorio, no al abrir la app (evita pedir permisos sin contexto).
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* si lo niega, igual queda programada la alarma; solo no se verá la notificación */ }

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            viewModel.getById(noteId)?.let { current = it }
        }
        segments = buildEditSegments(current.content)
        isEditing = noteId == 0L || !settings.doubleTapToEdit
        loaded = true
    }

    fun updateContentFromSegments(newSegments: List<EditSegment>) {
        segments = newSegments
        current = current.copy(content = segmentsToContent(newSegments))
    }

    fun insertImageAtActiveSegment(fileName: String) {
        val idx = activeSegmentIndex.coerceIn(0, segments.size - 1)
        val seg = segments.getOrNull(idx)
        val newSegments = segments.toMutableList()
        // Índice del tramo de texto que queda después de insertar, para dejar el
        // cursor ahí. Antes activeSegmentIndex no se actualizaba tras insertar, así
        // que al elegir varias imágenes seguidas todas se insertaban en el mismo
        // punto original (el cursor de texto quedaba "congelado" al inicio de ese
        // tramo), y el texto existente terminaba empujado después de la 2ª imagen.
        var nextActiveIndex = idx
        if (seg is EditSegment.TextSeg) {
            val text = seg.value.text
            val cursor = seg.value.selection.start.coerceIn(0, text.length)
            val before = text.substring(0, cursor)
            val after = text.substring(cursor)

            // Línea actual (donde está el cursor), delimitada por saltos de línea.
            val lineStart = before.lastIndexOf('\n').let { if (it == -1) 0 else it + 1 }
            val lineEndRel = after.indexOf('\n').let { if (it == -1) after.length else it }
            val currentLine = before.substring(lineStart) + after.substring(0, lineEndRel)

            if (currentLine.isBlank()) {
                // La línea donde está el cursor está vacía: la imagen la reemplaza
                // directamente, sin dejar líneas vacías de sobra.
                val prefix = before.substring(0, lineStart).removeSuffix("\n")
                val suffix = after.substring(lineEndRel).removePrefix("\n")

                newSegments.removeAt(idx)
                var insertAt = idx
                if (prefix.isNotEmpty()) {
                    newSegments.add(insertAt, EditSegment.TextSeg(TextFieldValue(prefix)))
                    insertAt++
                } else if (newSegments.getOrNull(insertAt - 1) is EditSegment.ImageSeg) {
                    // Si justo antes ya hay otra imagen (p. ej. se insertaron dos
                    // seguidas), dejamos un tramo de texto vacío como separador para
                    // que no queden dos imágenes pegadas sin forma de escribir entre
                    // ellas.
                    newSegments.add(insertAt, EditSegment.TextSeg(TextFieldValue("")))
                    insertAt++
                }
                newSegments.add(insertAt, EditSegment.ImageSeg(fileName, ""))
                insertAt++
                // Siempre dejamos exactamente un tramo (con o sin texto) después de la
                // imagen para poder seguir escribiendo, nunca dos vacíos.
                newSegments.add(insertAt, EditSegment.TextSeg(TextFieldValue(suffix)))
                nextActiveIndex = insertAt
            } else {
                newSegments[idx] = EditSegment.TextSeg(TextFieldValue(before))
                newSegments.add(idx + 1, EditSegment.ImageSeg(fileName, ""))
                newSegments.add(idx + 2, EditSegment.TextSeg(TextFieldValue(after)))
                nextActiveIndex = idx + 2
            }
        } else {
            newSegments.add(EditSegment.ImageSeg(fileName, ""))
            newSegments.add(EditSegment.TextSeg(TextFieldValue("")))
            nextActiveIndex = newSegments.size - 1
        }
        activeSegmentIndex = nextActiveIndex
        updateContentFromSegments(newSegments)
    }

    fun deleteImageSegment(idx: Int) {
        val seg = segments.getOrNull(idx) as? EditSegment.ImageSeg ?: return
        ImageStorage.deleteFile(context, seg.fileName)
        val newSegments = segments.toMutableList()
        newSegments.removeAt(idx)
        if (idx > 0 && idx < newSegments.size) {
            val prev = newSegments[idx - 1]
            val next = newSegments[idx]
            if (prev is EditSegment.TextSeg && next is EditSegment.TextSeg) {
                newSegments[idx - 1] = EditSegment.TextSeg(TextFieldValue(prev.value.text + next.value.text))
                newSegments.removeAt(idx)
            }
        }
        if (newSegments.isEmpty()) newSegments.add(EditSegment.TextSeg(TextFieldValue("")))
        updateContentFromSegments(newSegments)
    }

    fun wrapActiveSelection(marker: String) {
        val idx = activeSegmentIndex.coerceIn(0, segments.size - 1)
        val seg = segments.getOrNull(idx) as? EditSegment.TextSeg ?: return
        val text = seg.value.text
        val sel = seg.value.selection
        val start = sel.start.coerceIn(0, text.length)
        val end = sel.end.coerceIn(0, text.length)
        val selected = text.substring(start, end)
        val newText = text.substring(0, start) + marker + selected + marker + text.substring(end)
        val newSelection = if (start == end) {
            TextRange(start + marker.length)
        } else {
            TextRange(start + marker.length, end + marker.length)
        }
        val newSegments = segments.toMutableList()
        newSegments[idx] = EditSegment.TextSeg(TextFieldValue(newText, newSelection))
        updateContentFromSegments(newSegments)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            val fileName = if (settings.compressImages) {
                ImageStorage.compressFromUri(context, uri, settings.imageQuality)
            } else {
                ImageStorage.copyFromUri(context, uri)
            }
            if (fileName != null) insertImageAtActiveSegment(fileName)
        }
        if (uris.isNotEmpty()) focusManager.clearFocus(force = true)
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val fileName = pendingCameraFileName
        if (success && fileName != null) {
            if (settings.compressImages) ImageStorage.compressInPlace(context, fileName, settings.imageQuality)
            insertImageAtActiveSegment(fileName)
            focusManager.clearFocus(force = true)
        } else if (fileName != null) {
            ImageStorage.deleteFile(context, fileName)
        }
        pendingCameraFileName = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = ImageStorage.createCaptureFile(context)
            pendingCameraFileName = file.name
            cameraLauncher.launch(uri)
        }
    }

    // Sin esto, salir con el gesto/botón de retroceso del sistema (en vez de la
    // flecha propia de la app) descartaba cualquier cambio sin guardar, incluido
    // fijar/desfijar la nota.
    BackHandler(enabled = loaded && viewerStartPos != null) {
        viewerStartPos = null
    }
    BackHandler(
        enabled = loaded && viewerStartPos == null &&
            !(current.isPrivate && !biometricUnlockedForPrivate && !unlockedThisNote)
    ) {
        if (!isNoteEmpty(current)) viewModel.save(current)
        onBack()
    }

    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val isLockedForMe = current.isPrivate && !biometricUnlockedForPrivate && !unlockedThisNote

    if (isLockedForMe) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Nota privada") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Esta nota es privada")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { onRequestBiometric { unlockedThisNote = true } }) {
                        Text("Desbloquear")
                    }
                }
            }
        }
        return
    }

    fun applyViewerContentChange(newContent: String) {
        current = current.copy(content = newContent)
        if (isEditing) segments = buildEditSegments(newContent)
    }

    if (viewerStartPos != null) {
        val fileNames = extractImageFileNames(current.content)
        val parts = parseNoteContent(current.content).filterIsInstance<ContentPart.ImagePart>()
        val images = parts.map { NoteImage(path = "${ImageStorage.imagesDir(context)}/${it.fileName}", caption = it.caption) }
        ImageViewerScreen(
            images = images,
            startIndex = viewerStartPos ?: 0,
            onBack = { viewerStartPos = null },
            onDelete = { pos ->
                if (pos < fileNames.size) ImageStorage.deleteFile(context, fileNames[pos])
                applyViewerContentChange(removeImageOccurrence(current.content, pos))
                viewerStartPos = null
            }
        )
        return
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Agregar imagen") },
            text = { Text("Se insertará donde está el cursor.") },
            confirmButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
                }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Cámara")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showImageSourceDialog = false
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("Galería")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (settings.useTrash) "¿Enviar a la papelera?" else "¿Eliminar nota?") },
            text = {
                Text(
                    if (settings.useTrash) "Podrás restaurarla después desde la Papelera."
                    else "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    if (settings.useTrash) {
                        viewModel.moveToTrash(current)
                    } else {
                        extractImageFileNames(current.content).forEach { ImageStorage.deleteFile(context, it) }
                        viewModel.deleteForever(current)
                    }
                    onBack()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }

    if (showMoreSheet) {
        ModalBottomSheet(
            onDismissRequest = { showMoreSheet = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text("Color", style = MaterialTheme.typography.labelLarge)
                RgbColorPicker(
                    selectedHex = current.color,
                    onColorChange = { current = current.copy(color = it) }
                )
                Spacer(Modifier.height(12.dp))
                LabelsEditor(
                    selectedLabels = current.labels,
                    allLabels = allLabels,
                    onLabelsChange = { current = current.copy(labels = it) }
                )
            }
        }
    }

    if (showReminderSheet) {
        ReminderPickerSheet(
            initialMillis = current.reminderAt,
            onDismiss = { showReminderSheet = false },
            onConfirm = { millis ->
                current = current.copy(reminderAt = millis)
                showReminderSheet = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        context, android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onClear = {
                current = current.copy(reminderAt = null)
                showReminderSheet = false
            }
        )
    }

    val contentLayer = rememberGraphicsLayer()
    val bottomBarHeight = 56.dp

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        if (!isNoteEmpty(current)) viewModel.save(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // El usuario pidió que fijar/proteger solo aparezcan en modo
                    // vista, no en modo edición (son acciones rápidas sobre la nota
                    // ya guardada, no herramientas de edición del contenido).
                    if (!isEditing) {
                        IconButton(onClick = { current = current.copy(pinned = !current.pinned) }) {
                            Icon(
                                if (current.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = "Fijar"
                            )
                        }
                        IconButton(onClick = {
                            if (current.isPrivate) {
                                current = current.copy(isPrivate = false)
                            } else {
                                onRequestBiometric {
                                    current = current.copy(isPrivate = true)
                                    unlockedThisNote = true
                                }
                            }
                        }) {
                            Icon(
                                if (current.isPrivate) Icons.Filled.Lock else Icons.Outlined.LockOpen,
                                contentDescription = "Privada"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            // Antes era un BottomAppBar de Material3: ese componente reserva
            // ~80dp de alto con relleno pensado para llevar un FAB embebido, que
            // acá no usamos, así que sobraba una franja enorme vacía. Ahora es
            // un contenedor propio con una altura fija y compacta (56dp, la
            // misma que un TopAppBar chico) que además es semitransparente y
            // desenfoca lo que hay detrás (el contenido de la nota, que se deja
            // scrollear por debajo de la barra) para un efecto "vidrio
            // esmerilado" en vez de un panel sólido.
            GlassBottomBar(
                contentLayer = contentLayer,
                height = bottomBarHeight,
                centered = isEditing
            ) {
                if (current.deletedAt != null) {
                    IconButton(onClick = {
                        extractImageFileNames(current.content).forEach { ImageStorage.deleteFile(context, it) }
                        viewModel.deleteForever(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = "Eliminar para siempre")
                    }
                    IconButton(onClick = {
                        viewModel.restore(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restaurar")
                    }
                } else {
                    IconButton(onClick = {
                        if (settings.confirmBeforeDelete) {
                            showDeleteConfirm = true
                        } else if (settings.useTrash) {
                            viewModel.moveToTrash(current)
                            onBack()
                        } else {
                            extractImageFileNames(current.content).forEach { ImageStorage.deleteFile(context, it) }
                            viewModel.deleteForever(current)
                            onBack()
                        }
                    }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Borrar")
                    }
                }
                if (current.type == NoteType.TEXT && isEditing) {
                    IconButton(onClick = { showImageSourceDialog = true }) {
                        Icon(Icons.Filled.Image, contentDescription = "Insertar imagen")
                    }
                    IconButton(onClick = { wrapActiveSelection("**") }) {
                        Icon(Icons.Filled.FormatBold, contentDescription = "Negrita")
                    }
                    IconButton(onClick = { wrapActiveSelection("*") }) {
                        Icon(Icons.Filled.FormatItalic, contentDescription = "Cursiva")
                    }
                    IconButton(onClick = { wrapActiveSelection("~~") }) {
                        Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Tachado")
                    }
                    IconButton(onClick = { wrapActiveSelection("`") }) {
                        Icon(Icons.Filled.Code, contentDescription = "Monoespaciado")
                    }
                }
                if (isEditing) {
                    IconButton(onClick = { showMoreSheet = true }) {
                        Icon(Icons.Filled.Palette, contentDescription = "Color y etiquetas")
                    }
                    IconButton(onClick = { showReminderSheet = true }) {
                        Icon(
                            if (current.reminderAt != null) Icons.Filled.Alarm else Icons.Outlined.AlarmAdd,
                            contentDescription = "Recordatorio",
                            tint = if (current.reminderAt != null) MaterialTheme.colorScheme.primary else LocalContentColor.current
                        )
                    }
                }
                // Antes este botón vivía en la barra de arriba y solo aparecía para
                // notas de texto: las notas de tipo checklist no tenían forma de
                // volver a modo edición una vez guardadas (con "doble toque para
                // editar" activado, quedaban bloqueadas en solo lectura para
                // siempre). Ahora vive abajo, junto al resto de acciones, y
                // funciona para ambos tipos de nota.
                IconButton(onClick = {
                    val goingToEdit = !isEditing
                    if (goingToEdit && current.type == NoteType.TEXT) {
                        segments = buildEditSegments(current.content)
                    }
                    isEditing = goingToEdit
                }) {
                    Icon(
                        if (isEditing) Icons.Filled.RemoveRedEye else Icons.Outlined.EditNote,
                        contentDescription = if (isEditing) "Vista previa" else "Editar"
                    )
                }
                // El botón de guardar solo tiene sentido en modo edición: en modo
                // vista no hay nada que guardar (y el back ya guarda solo si hubo
                // cambios), así que antes no debía mostrarse ahí.
                if (isEditing) {
                    IconButton(onClick = {
                        if (!isNoteEmpty(current)) {
                            viewModel.save(current) { id ->
                                if (noteId == 0L) current = current.copy(id = id)
                            }
                        }
                        onBack()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Guardar")
                    }
                }
            }
        }
    ) { padding ->
        val readModeGesture = if (!isEditing) {
            Modifier.pointerInput(Unit) {
                detectDoubleTapToEdit {
                    segments = buildEditSegments(current.content)
                    isEditing = true
                }
            }
        } else Modifier

        Column(
            modifier = Modifier
                .fillMaxSize()
                // OJO: a propósito NO aplicamos el padding inferior que da el
                // Scaffold acá. Si lo hiciéramos, el contenido nunca se dibujaría
                // detrás de la barra inferior y no habría nada que desenfocar
                // (desenfocar "nada" no se nota). En su lugar dejamos que el
                // contenido llegue hasta el fondo real de la pantalla, y más abajo
                // le agregamos un espacio en blanco del alto de la barra para que
                // el texto/checklist no quede tapado al hacer scroll hasta el final.
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(LocalLayoutDirection.current),
                    end = padding.calculateEndPadding(LocalLayoutDirection.current)
                )
                .padding(horizontal = 12.dp)
                // Graba todo lo que se dibuja acá (título, texto, checklist,
                // imágenes) en una "capa" que la barra de abajo puede volver a
                // dibujar recortada y desenfocada, logrando el efecto de vidrio
                // esmerilado sin duplicar la UI real.
                .drawWithContent {
                    contentLayer.record { this@drawWithContent.drawContent() }
                    drawContent()
                }
        ) {
            if (isEditing) {
                FlatTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Título") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            } else if (current.title.isNotBlank()) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (current.type == NoteType.CHECKLIST) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .then(readModeGesture)
                    ) {
                        ChecklistEditor(
                            items = current.checklistItems,
                            checkboxPosition = settings.checkboxPosition,
                            readOnly = !isEditing,
                            onItemsChange = { newItems ->
                                val finalItems = if (settings.autoSortChecked) {
                                    newItems.sortedBy { it.checked }
                                } else newItems
                                current = current.copy(checklistItems = finalItems)
                            }
                        )
                        // Despeje para que el último ítem no quede tapado por la
                        // barra inferior flotante y semitransparente.
                        Spacer(Modifier.height(bottomBarHeight + 12.dp))
                    }
                } else if (isEditing) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        segments.forEachIndexed { index, segment ->
                            when (segment) {
                                is EditSegment.TextSeg -> {
                                    // Solo el campo activo necesita pedir "traeme a la
                                    // vista" (no tiene sentido, y sería más costoso,
                                    // hacerlo para todos los tramos de texto de la nota).
                                    val bringIntoViewRequester = if (index == activeSegmentIndex) {
                                        remember(index) { BringIntoViewRequester() }
                                    } else null
                                    FlatTextField(
                                        value = segment.value,
                                        onValueChange = { value ->
                                            val newSegments = segments.toMutableList()
                                            newSegments[index] = EditSegment.TextSeg(value)
                                            updateContentFromSegments(newSegments)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(
                                                if (segments.size == 1) Modifier.heightIn(min = 560.dp)
                                                else Modifier
                                            )
                                            .onFocusChanged { if (it.isFocused) activeSegmentIndex = index },
                                        placeholder = { Text("Escribe...") },
                                        bringIntoViewRequester = bringIntoViewRequester
                                    )
                                }
                                is EditSegment.ImageSeg -> {
                                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                        Box {
                                            val imageIndex = segments.take(index).count { it is EditSegment.ImageSeg }
                                            AsyncImage(
                                                model = File(ImageStorage.imagesDir(context), segment.fileName),
                                                contentDescription = null,
                                                contentScale = ContentScale.FillWidth,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(16.dp))
                                                    .clickable { viewerStartPos = imageIndex }
                                            )
                                            IconButton(
                                                onClick = { deleteImageSegment(index) },
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(28.dp)
                                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Quitar imagen",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                            if (segment.caption.isBlank() && index !in captionActiveIndices) {
                                                TextButton(
                                                    onClick = { captionActiveIndices = captionActiveIndices + index },
                                                    modifier = Modifier
                                                        .align(Alignment.BottomStart)
                                                        .padding(6.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    colors = ButtonDefaults.textButtonColors(
                                                        containerColor = Color.Black.copy(alpha = 0.5f),
                                                        contentColor = Color.White
                                                    )
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Descripción", style = MaterialTheme.typography.labelSmall)
                                                }
                                            }
                                        }
                                        if (segment.caption.isNotBlank() || index in captionActiveIndices) {
                                            CompactCaptionField(
                                                value = segment.caption,
                                                onValueChange = { caption ->
                                                    val newSegments = segments.toMutableList()
                                                    newSegments[index] = segment.copy(caption = caption)
                                                    updateContentFromSegments(newSegments)
                                                },
                                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(bottomBarHeight + 12.dp))
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .then(readModeGesture)
                    ) {
                        NoteContentView(
                            content = current.content,
                            onImageClick = { idx -> viewerStartPos = idx }
                        )
                        Spacer(Modifier.height(bottomBarHeight + 12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LabelsEditor(
    selectedLabels: List<String>,
    allLabels: List<String>,
    onLabelsChange: (List<String>) -> Unit
) {
    var newLabel by remember { mutableStateOf("") }
    Column {
        Text("Etiquetas", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val combined = (allLabels + selectedLabels).distinct()
            combined.forEach { label ->
                val selected = label in selectedLabels
                FilterChip(
                    selected = selected,
                    onClick = {
                        onLabelsChange(if (selected) selectedLabels - label else selectedLabels + label)
                    },
                    label = { Text(label) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FlatTextField(
                value = newLabel,
                onValueChange = { newLabel = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Nueva etiqueta") },
                singleLine = true
            )
            TextButton(onClick = {
                if (newLabel.isNotBlank() && newLabel !in selectedLabels) {
                    onLabelsChange(selectedLabels + newLabel.trim())
                    newLabel = ""
                }
            }) { Text("Agregar") }
        }
    }
}

// Barra inferior "de vidrio esmerilado" para el editor de notas.
//
// Reemplaza al BottomAppBar de Material3 (que reservaba ~80dp con relleno
// pensado para un FAB embebido que acá no se usa). Esta versión:
//  - tiene una altura fija y compacta (parámetro `height`, 56dp desde donde se
//    llama), en vez de la altura excesiva por defecto.
//  - centra sus botones cuando `centered = true` (modo edición); en modo
//    vista los deja alineados al inicio, ya que ahí hay menos botones y
//    centrarlos se vería raro con tanto espacio vacío alrededor.
//  - es semitransparente y desenfoca lo que hay detrás en vez de tapar todo
//    con un panel sólido. El contenido de la nota (en NoteEditScreen) se
//    deja dibujar por debajo de esta barra a propósito, grabando su dibujo
//    en `contentLayer`; acá simplemente volvemos a dibujar (recortada a esta
//    franja) esa misma grabación con un desenfoque real encima, así que lo
//    que se ve "a través" de la barra es efectivamente el contenido real que
//    hay detrás, no una imitación.
@Composable
private fun GlassBottomBar(
    contentLayer: GraphicsLayer,
    height: Dp,
    centered: Boolean,
    content: @Composable RowScope.() -> Unit
) {
    val barTint = MaterialTheme.colorScheme.surface
    Box(modifier = Modifier.fillMaxWidth().height(height)) {
        // Capa de fondo: copia recortada y desenfocada del contenido de la
        // nota que queda "detrás" de esta barra, más un tinte semitransparente.
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    clip = true
                    // El desenfoque real (RenderEffect) solo existe desde
                    // Android 12 (API 31). En versiones anteriores nos
                    // quedamos con la transparencia sola: se sigue viendo
                    // "liviana" aunque sin el desenfoque, degradación
                    // razonable en vez de romper algo.
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        renderEffect = BlurEffect(26f, 26f, TileMode.Clamp)
                    }
                }
                .drawWithContent {
                    drawRect(barTint.copy(alpha = 0.55f))
                    val layerSize = contentLayer.size
                    if (layerSize.height > 0) {
                        // El contenido grabado empieza en la parte de arriba de
                        // la pantalla; a esta barra le corresponde solo su
                        // franja final (la más cercana al borde inferior), así
                        // que lo trasladamos hacia arriba para recortar
                        // justo esa porción.
                        translate(top = -(layerSize.height - size.height)) {
                            drawLayer(contentLayer)
                        }
                    }
                }
        )
        // Capa de primer plano: los botones de verdad, sin desenfocar.
        Row(
            modifier = Modifier
                .matchParentSize()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
            content = content
        )
    }
}

// Selector de fecha y hora para el recordatorio de una nota, usando los
// componentes nativos de Material3 (DatePicker + TimeInput) en vez de traer
// una librería aparte.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderPickerSheet(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    onClear: () -> Unit
) {
    val cal = remember {
        java.util.Calendar.getInstance().apply {
            if (initialMillis != null) {
                timeInMillis = initialMillis
            } else {
                add(java.util.Calendar.HOUR_OF_DAY, 1)
                set(java.util.Calendar.MINUTE, 0)
            }
        }
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = cal.timeInMillis)
    val timePickerState = rememberTimePickerState(
        initialHour = cal.get(java.util.Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(java.util.Calendar.MINUTE),
        is24Hour = true
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Recordatorio", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            DatePicker(state = datePickerState, showModeToggle = false)
            Spacer(Modifier.height(8.dp))
            TimeInput(state = timePickerState)
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (initialMillis != null) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Filled.AlarmOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Quitar")
                    }
                    Spacer(Modifier.width(8.dp))
                }
                TextButton(onClick = onDismiss) { Text("Cancelar") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    val selectedDateMillis = datePickerState.selectedDateMillis
                    if (selectedDateMillis != null) {
                        val target = java.util.Calendar.getInstance().apply {
                            timeInMillis = selectedDateMillis
                            // DatePicker devuelve la fecha en UTC a medianoche;
                            // le aplicamos encima la hora local elegida en el
                            // TimeInput para no arrastrar el desfase de huso
                            // horario a la hora final.
                            set(java.util.Calendar.HOUR_OF_DAY, timePickerState.hour)
                            set(java.util.Calendar.MINUTE, timePickerState.minute)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        onConfirm(target.timeInMillis)
                    }
                }) {
                    Text("Guardar")
                }
            }
            Text(
                "El recordatorio necesita el permiso de notificaciones y, en Android 12+, el de \"alarmas exactas\" en Ajustes del sistema para sonar puntual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}
