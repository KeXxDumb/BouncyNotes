package com.example.notes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.data.ImageStorage
import com.example.notes.data.Note
import com.example.notes.data.NoteImage
import com.example.notes.data.NoteType
import com.example.notes.ui.components.ChecklistEditor
import com.example.notes.ui.components.ColorPickerRow
import com.example.notes.ui.components.ImageAttachmentsSection

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NoteEditScreen(
    noteId: Long,
    initialType: NoteType,
    viewModel: NoteViewModel,
    biometricUnlockedForPrivate: Boolean,
    onRequestBiometric: (onSuccess: () -> Unit) -> Unit,
    allLabels: List<String>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf(Note(type = initialType)) }
    var unlockedThisNote by remember { mutableStateOf(false) }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            viewModel.getById(noteId)?.let { current = it }
        }
        loaded = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            val newImages = uris.mapNotNull { uri ->
                ImageStorage.copyFromUri(context, uri)?.let { NoteImage(path = it) }
            }
            current = current.copy(images = current.images + newImages)
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            current = current.copy(images = current.images + NoteImage(path = path))
        } else if (path != null) {
            ImageStorage.deleteFile(path)
        }
        pendingCameraPath = null
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (file, uri) = ImageStorage.createCaptureFile(context)
            pendingCameraPath = file.absolutePath
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

    if (viewerIndex != null) {
        ImageViewerScreen(
            images = current.images,
            startIndex = viewerIndex ?: 0,
            onBack = { viewerIndex = null },
            onCaptionChange = { idx, caption ->
                current = current.copy(
                    images = current.images.toMutableList().also { it[idx] = it[idx].copy(caption = caption) }
                )
            },
            onDelete = { idx ->
                ImageStorage.deleteFile(current.images[idx].path)
                current = current.copy(images = current.images.toMutableList().also { it.removeAt(idx) })
                viewerIndex = null
            }
        )
        return
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
                            viewModel.moveToTrash(current)
                            onBack()
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = current.title,
                    onValueChange = { current = current.copy(title = it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Título") },
                    textStyle = MaterialTheme.typography.titleLarge,
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                ColorPickerRow(
                    selected = current.color,
                    onSelect = { current = current.copy(color = it) }
                )
            }
            item {
                ImageAttachmentsSection(
                    images = current.images,
                    onAddFromCamera = { cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    onAddFromGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onImageClick = { index -> viewerIndex = index },
                    onImageRemove = { index ->
                        val img = current.images[index]
                        ImageStorage.deleteFile(img.path)
                        current = current.copy(images = current.images.toMutableList().also { it.removeAt(index) })
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
            item {
                if (current.type == NoteType.CHECKLIST) {
                    ChecklistEditor(
                        items = current.checklistItems,
                        onItemsChange = { current = current.copy(checklistItems = it) }
                    )
                } else {
                    OutlinedTextField(
                        value = current.content,
                        onValueChange = { current = current.copy(content = it) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                        placeholder = { Text("Escribe tu nota...") }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
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
