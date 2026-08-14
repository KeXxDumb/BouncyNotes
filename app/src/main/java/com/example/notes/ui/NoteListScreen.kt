package com.example.notes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.notes.data.Note
import com.example.notes.data.NoteType
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    biometricUnlockedForPrivate: Boolean,
    onRequestBiometric: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onAddClick: (NoteType) -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val labelFilter by viewModel.labelFilter.collectAsState()
    val labels by viewModel.allLabels.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var fabExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(viewMode) {
        if (viewMode == ViewMode.PRIVATE && !biometricUnlockedForPrivate) {
            onRequestBiometric()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                Text("Notas", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                NavigationDrawerItem(
                    label = { Text("Todas las notas") },
                    selected = viewMode == ViewMode.ALL && labelFilter == null,
                    icon = { Icon(Icons.Filled.Notes, contentDescription = null) },
                    onClick = {
                        viewModel.setViewMode(ViewMode.ALL)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Privadas") },
                    selected = viewMode == ViewMode.PRIVATE,
                    icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    onClick = {
                        viewModel.setViewMode(ViewMode.PRIVATE)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Archivadas") },
                    selected = viewMode == ViewMode.ARCHIVED,
                    icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                    onClick = {
                        viewModel.setViewMode(ViewMode.ARCHIVED)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Papelera") },
                    selected = viewMode == ViewMode.TRASH,
                    icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                    onClick = {
                        viewModel.setViewMode(ViewMode.TRASH)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                if (labels.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text(
                        "Etiquetas",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    labels.forEach { label ->
                        NavigationDrawerItem(
                            label = { Text(label) },
                            selected = labelFilter == label,
                            icon = { Icon(Icons.Filled.Label, contentDescription = null) },
                            onClick = {
                                viewModel.setLabelFilter(label)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = query,
                            onValueChange = viewModel::setQuery,
                            placeholder = { Text("Buscar notas...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menú")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (viewMode == ViewMode.ALL || viewMode == ViewMode.PRIVATE) {
                    Column(horizontalAlignment = Alignment.End) {
                        if (fabExpanded) {
                            ExtendedFloatingActionButton(
                                onClick = { fabExpanded = false; onAddClick(NoteType.CHECKLIST) },
                                icon = { Icon(Icons.Filled.Checklist, contentDescription = null) },
                                text = { Text("Checklist") }
                            )
                            Spacer(Modifier.height(8.dp))
                            ExtendedFloatingActionButton(
                                onClick = { fabExpanded = false; onAddClick(NoteType.TEXT) },
                                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                                text = { Text("Nota") }
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                            Icon(
                                if (fabExpanded) Icons.Filled.Close else Icons.Filled.Add,
                                contentDescription = "Nueva nota"
                            )
                        }
                    }
                }
            }
        ) { padding ->
            when {
                viewMode == ViewMode.PRIVATE && !biometricUnlockedForPrivate -> {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("Verifica tu identidad para ver tus notas privadas")
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = onRequestBiometric) { Text("Desbloquear") }
                        }
                    }
                }
                notes.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                        Text("No hay notas aquí todavía", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> {
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(8.dp),
                        verticalItemSpacing = 8.dp,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(notes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note.id) },
                                onTogglePin = { viewModel.togglePin(note) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, onTogglePin: () -> Unit) {
    val bg = note.color?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.surfaceContainerHigh

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = bg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            if (note.images.isNotEmpty()) {
                AsyncImage(
                    model = File(note.images.first().path),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "(Sin título)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.pinned) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = "Fijada",
                        modifier = Modifier.size(16.dp).clickable(onClick = onTogglePin)
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            if (note.type == NoteType.CHECKLIST) {
                note.checklistItems.take(4).forEach { item ->
                    Text(
                        text = (if (item.checked) "☑ " else "☐ ") + item.text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (note.checklistItems.size > 4) {
                    Text("+${note.checklistItems.size - 4} más", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (note.labels.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    note.labels.take(3).forEach { label ->
                        AssistChip(onClick = {}, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
            }
        }
    }
}
