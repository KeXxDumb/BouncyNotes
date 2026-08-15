package com.example.notes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.notes.data.AppSettings
import com.example.notes.data.ContentPart
import com.example.notes.data.ImageStorage
import com.example.notes.data.Note
import com.example.notes.data.NoteImage
import com.example.notes.data.NoteType
import com.example.notes.data.buildImageTag
import com.example.notes.data.extractImageFileNames
import com.example.notes.data.parseNoteContent
import com.example.notes.data.removeImageOccurrence
import com.example.notes.data.updateImageCaption
import com.example.notes.ui.components.ChecklistEditor
import com.example.notes.ui.components.NoteContentView
import com.example.notes.ui.components.RgbColorPicker
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
    return parts.map { part ->
        when (part) {
            is ContentPart.TextPart -> EditSegment.TextSeg(TextFieldValue(part.text))
            is ContentPart.ImagePart -> EditSegment.ImageSeg(part.fileName, part.caption)
        }
    }
}

private fun segmentsToContent(segments: List<EditSegment>): String =
    segments.joinToString("") { seg ->
        when (seg) {
            is EditSegment.TextSeg -> seg.value.text
            is EditSegment.ImageSeg -> buildImageTag(seg.fileName, seg.caption)
        }
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    var isEditing by remember { mutableStateOf(true) }

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
        if (seg is EditSegment.TextSeg) {
            val text = seg.value.text
            val cursor = seg.value.selection.start.coerceIn(0, text.length)
            val before = text.substring(0, cursor)
            val after = text.substring(cursor)
            newSegments[idx] = EditSegment.TextSeg(TextFieldValue(before))
            newSegments.add(idx + 1, EditSegment.ImageSeg(fileName, ""))
            newSegments.add(idx + 2, EditSegment.TextSeg(TextFieldValue(after)))
        } else {
            newSegments.add(EditSegment.ImageSeg(fileName, ""))
            newSegments.add(EditSegment.TextSeg(TextFieldValue("")))
        }
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
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val fileName = pendingCameraFileName
        if (success && fileName != null) {
            if (settings.compressImages) ImageStorage.compressInPlace(context, fileName, settings.imageQuality)
            insertImageAtActiveSegment(fileName)
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
            onCaptionChange = { pos, caption ->
                applyViewerContentChange(updateImageCaption(current.content, pos, caption))
            },
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
            title = { Text("¿Enviar a la papelera?") },
            text = { Text("Podrás restaurarla después desde la Papelera.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.moveToTrash(current)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.save(current)
                        onBack()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    if (current.type == NoteType.TEXT) {
                        IconButton(onClick = {
                            val goingToEdit = !isEditing
                            if (goingToEdit) segments = buildEditSegments(current.content)
                            isEditing = goingToEdit
                        }) {
                            Icon(
                                if (isEditing) Icons.Filled.RemoveRedEye else Icons.Outlined.EditNote,
                                contentDescription = if (isEditing) "Vista previa" else "Editar"
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
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
                    IconButton(onClick = { current = current.copy(archived = !current.archived) }) {
                        Icon(Icons.Filled.Archive, contentDescription = "Archivar")
                    }
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
                            } else {
                                viewModel.moveToTrash(current)
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
                    }
                    IconButton(onClick = {
                        viewModel.save(current) { id ->
                            if (noteId == 0L) current = current.copy(id = id)
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
                detectTapGestures(onDoubleTap = {
                    segments = buildEditSegments(current.content)
                    isEditing = true
                })
            }
        } else Modifier

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            if (isEditing) {
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Título") },
                    textStyle = MaterialTheme.typography.titleMedium,
                    singleLine = true
                )
                Spacer(Modifier.height(4.dp))
            } else if (current.title.isNotBlank()) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (current.type == NoteType.CHECKLIST) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
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
                    }
                } else if (isEditing) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        segments.forEachIndexed { index, segment ->
                            when (segment) {
                                is EditSegment.TextSeg -> {
                                    OutlinedTextField(
                                        value = segment.value,
                                        onValueChange = { value ->
                                            val newSegments = segments.toMutableList()
                                            newSegments[index] = EditSegment.TextSeg(value)
                                            updateContentFromSegments(newSegments)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onFocusChanged { if (it.isFocused) activeSegmentIndex = index },
                                        placeholder = { Text("Escribe...") }
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
                                        }
                                        OutlinedTextField(
                                            value = segment.caption,
                                            onValueChange = { caption ->
                                                val newSegments = segments.toMutableList()
                                                newSegments[index] = segment.copy(caption = caption)
                                                updateContentFromSegments(newSegments)
                                            },
                                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                            placeholder = { Text("Descripción (opcional)") },
                                            singleLine = true
                                        )
                                    }
                                }
                            }
                        }
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
            OutlinedTextField(
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
