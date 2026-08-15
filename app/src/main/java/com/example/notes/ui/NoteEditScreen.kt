package com.example.notes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
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
import com.example.notes.ui.components.ColorPickerRow
import com.example.notes.ui.components.NoteContentView

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
    var textFieldValue by remember { mutableStateOf(TextFieldValue("")) }
    var unlockedThisNote by remember { mutableStateOf(false) }
    var pendingCameraFileName by remember { mutableStateOf<String?>(null) }
    var viewerStartPos by remember { mutableStateOf<Int?>(null) }
    var showImageSourceDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isEditing by remember { mutableStateOf(true) }

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            viewModel.getById(noteId)?.let { current = it }
        }
        textFieldValue = TextFieldValue(current.content)
        isEditing = noteId == 0L || !settings.doubleTapToEdit
        loaded = true
    }

    fun insertImageTag(fileName: String) {
        val tag = buildImageTag(fileName)
        val selection = textFieldValue.selection
        val text = textFieldValue.text
        val start = selection.start.coerceIn(0, text.length)
        val end = selection.end.coerceIn(0, text.length)
        val prefix = text.substring(0, start)
        val needsNewlineBefore = prefix.isNotEmpty() && !prefix.endsWith("\n")
        val insertion = (if (needsNewlineBefore) "\n" else "") + tag + "\n"
        val newText = text.substring(0, start) + insertion + text.substring(end)
        val newCursor = start + insertion.length
        textFieldValue = TextFieldValue(newText, TextRange(newCursor))
        current = current.copy(content = newText)
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
            if (fileName != null) insertImageTag(fileName)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val fileName = pendingCameraFileName
        if (success && fileName != null) {
            if (settings.compressImages) ImageStorage.compressInPlace(context, fileName, settings.imageQuality)
            insertImageTag(fileName)
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

    if (viewerStartPos != null) {
        val fileNames = extractImageFileNames(current.content)
        val parts = parseNoteContent(current.content).filterIsInstance<ContentPart.ImagePart>()
        val images = parts.map { NoteImage(path = "${ImageStorage.imagesDir(context)}/${it.fileName}", caption = it.caption) }
        ImageViewerScreen(
            images = images,
            startIndex = viewerStartPos ?: 0,
            onBack = { viewerStartPos = null },
            onCaptionChange = { pos, caption ->
                val newContent = updateImageCaption(current.content, pos, caption)
                current = current.copy(content = newContent)
                textFieldValue = TextFieldValue(newContent, textFieldValue.selection)
            },
            onDelete = { pos ->
                if (pos < fileNames.size) ImageStorage.deleteFile(context, fileNames[pos])
                val newContent = removeImageOccurrence(current.content, pos)
                current = current.copy(content = newContent)
                textFieldValue = TextFieldValue(newContent)
                viewerStartPos = null
            }
        )
        return
    }

    if (showImageSourceDialog) {
        AlertDialog(
            onDismissRequest = { showImageSourceDialog = false },
            title = { Text("Agregar imagen") },
            text = { Text("Se insertará donde está el cursor. ¿Desde dónde la quieres agregar?") },
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == 0L) "Nueva nota" else "Editar nota") },
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
                        IconButton(onClick = { isEditing = !isEditing }) {
                            Icon(
                                if (isEditing) Icons.Filled.RemoveRedEye else Icons.Outlined.EditNote,
                                contentDescription = if (isEditing) "Vista previa" else "Editar"
                            )
                        }
                    }
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
                    IconButton(onClick = {
                        viewModel.save(current) { id ->
                            if (noteId == 0L) current = current.copy(id = id)
                        }
                        onBack()
                    }) {
                        Icon(Icons.Filled.Check, contentDescription = "Guardar")
                    }
                }
            )
        }
    ) { padding ->
        val readModeGesture = if (!isEditing) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { isEditing = true })
            }
        } else Modifier

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
                .then(readModeGesture),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                if (isEditing) {
                    OutlinedTextField(
                        value = current.title,
                        onValueChange = { current = current.copy(title = it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Título") },
                        textStyle = MaterialTheme.typography.titleLarge,
                        singleLine = true
                    )
                } else {
                    Text(
                        text = current.title.ifBlank { "(Sin título)" },
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
            if (isEditing) {
                item {
                    ColorPickerRow(
                        selected = current.color,
                        onSelect = { current = current.copy(color = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
            if (current.type == NoteType.CHECKLIST) {
                item {
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
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                if (isEditing) {
                    item {
                        TextButton(onClick = {
                            showImageSourceDialog = true
                        }) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Insertar imagen aquí")
                        }
                        OutlinedTextField(
                            value = textFieldValue,
                            onValueChange = {
                                textFieldValue = it
                                current = current.copy(content = it.text)
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
                            placeholder = { Text("Escribe tu nota... usa \"Insertar imagen\" para colocarla justo donde está el cursor.") }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                } else {
                    item {
                        NoteContentView(
                            content = current.content,
                            onImageClick = { idx -> viewerStartPos = idx }
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            if (isEditing) {
                item {
                    LabelsEditor(
                        selectedLabels = current.labels,
                        allLabels = allLabels,
                        onLabelsChange = { current = current.copy(labels = it) }
                    )
                    Spacer(Modifier.height(24.dp))
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
