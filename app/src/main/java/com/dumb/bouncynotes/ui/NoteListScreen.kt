package com.dumb.bouncynotes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.CheckboxPosition
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.Note
import com.dumb.bouncynotes.data.NoteLayout
import com.dumb.bouncynotes.data.NoteType
import com.dumb.bouncynotes.data.parseNoteContent
import com.dumb.bouncynotes.data.stripFormattingMarkers
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    viewModel: NoteViewModel,
    settings: AppSettings,
    biometricUnlockedForPrivate: Boolean,
    onRequestBiometric: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onAddClick: (NoteType) -> Unit,
    onOpenSettings: () -> Unit
) {
    val notes by viewModel.notes.collectAsState()
    val query by viewModel.query.collectAsState()
    val viewMode by viewModel.viewMode.collectAsState()
    val labelFilter by viewModel.labelFilter.collectAsState()
    val labels by viewModel.allLabels.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var fabExpanded by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode = selectedIds.isNotEmpty()

    // Bug reportado: abrir Ajustes, cerrarlo y tocar rápido y repetido la
    // esquina superior izquierda dejaba la pantalla en negro. Con el log de
    // diagnóstico (tag "BouncyDrawerDebug", ya retirado) se confirmó que no
    // había ningún gesto de swipe disparándose de más: el problema era que
    // un segundo toque "fantasma" llegaba a alcanzar a scope.launch { } y
    // disparaba una segunda apertura del drawer, o una segunda navegación a
    // Ajustes, mientras la transición anterior todavía estaba en curso y
    // NoteListScreen estaba a medio recomponer al volver. Eso se arregla en
    // dos frentes: 1. navigateSafe()/popBackStackSafe() en MainActivity, que
    // ignoran una navegación si la pantalla actual aún no llegó a RESUMED, y
    // 2. acá abajo, ignorando toques en el botón de menú mientras el drawer
    // ya está animando (isAnimationRunning), para que un doble-toque
    // accidental no dispare dos animaciones open()/close() superpuestas.
    var navigatingToSettings by remember { mutableStateOf(false) }

    LaunchedEffect(viewMode, labelFilter) {
        selectedIds = emptySet()
    }

    LaunchedEffect(viewMode) {
        if (viewMode == ViewMode.PRIVATE && !biometricUnlockedForPrivate) {
            onRequestBiometric()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (settings.backgroundImagePath != null) {
            AsyncImage(
                model = File(ImageStorage.imagesDir(context), settings.backgroundImagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = settings.backgroundImageOpacity,
                colorFilter = if (settings.backgroundMonochrome) {
                    ColorFilter.tint(MaterialTheme.colorScheme.primary, BlendMode.Color)
                } else null,
                modifier = Modifier.fillMaxSize()
            )
            if (settings.backgroundFade) {
                // Un radialGradient dibuja un círculo, así que en una imagen
                // rectangular solo se nota el desvanecido cerca de las esquinas (que
                // es donde el círculo realmente se acerca al borde); los bordes
                // superior/inferior/laterales quedaban casi sin desvanecer. En vez de
                // un círculo, desvanecemos cada borde por separado con un degradado
                // lineal (arriba, abajo, izquierda, derecha); donde se superponen
                // (las esquinas) el desvanecido se nota un poco más fuerte, que es
                // justamente el efecto de viñeta esperado.
                val fadeColor = MaterialTheme.colorScheme.background.copy(alpha = settings.backgroundFadeOpacity)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val fadeWidth = size.width * 0.35f
                            val fadeHeight = size.height * 0.35f
                            onDrawBehind {
                                // Izquierda
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colorStops = arrayOf(0f to fadeColor, 1f to Color.Transparent),
                                        startX = 0f,
                                        endX = fadeWidth
                                    )
                                )
                                // Derecha
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colorStops = arrayOf(0f to Color.Transparent, 1f to fadeColor),
                                        startX = size.width - fadeWidth,
                                        endX = size.width
                                    )
                                )
                                // Arriba
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(0f to fadeColor, 1f to Color.Transparent),
                                        startY = 0f,
                                        endY = fadeHeight
                                    )
                                )
                                // Abajo
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(0f to Color.Transparent, 1f to fadeColor),
                                        startY = size.height - fadeHeight,
                                        endY = size.height
                                    )
                                )
                            }
                        }
                )
            }
        }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Por defecto el drawer tiene un gesto de "deslizar desde el borde
        // izquierdo para abrir" activo en TODA la altura de la pantalla, no solo
        // sobre el botón de 3 líneas. Ese detector de gesto es independiente del
        // botón y quedaba viviendo ahí incluso recién se vuelve de Ajustes; un
        // toque justo en esa franja del borde (más una recomposición reciente al
        // volver de otra pantalla) terminaba en el drawer quedando a medio
        // renderizar: pantalla negra. Lo desactivamos cuando el drawer está
        // cerrado (solo se abre con el botón, de forma controlada) y lo dejamos
        // activo cuando ya está abierto, para poder cerrarlo deslizando como es
        // normal.
        gesturesEnabled = drawerState.isOpen,
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
                if (settings.useTrash) {
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
                }
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
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    label = { Text("Ajustes") },
                    selected = false,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    onClick = {
                        // Ignoramos toques repetidos mientras ya hay una
                        // navegación a Ajustes en curso: esto era justamente lo
                        // que dejaba dos animaciones/navegaciones superpuestas
                        // y terminaba en pantalla negra.
                        if (!navigatingToSettings) {
                            navigatingToSettings = true
                            scope.launch {
                                drawerState.close()
                                onOpenSettings()
                                navigatingToSettings = false
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
        Scaffold(
            containerColor = if (settings.backgroundImagePath != null) Color.Transparent else MaterialTheme.colorScheme.background,
            topBar = {
                if (selectionMode) {
                    TopAppBar(
                        title = { Text("${selectedIds.size} seleccionadas") },
                        navigationIcon = {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancelar selección")
                            }
                        },
                        actions = {
                            if (viewMode != ViewMode.TRASH) {
                                val selectedNotes = notes.filter { it.id in selectedIds }
                                val anyUnpinned = selectedNotes.any { !it.pinned }
                                val anyPinned = selectedNotes.any { it.pinned }
                                if (anyUnpinned) {
                                    IconButton(onClick = {
                                        viewModel.setPinnedForIds(selectedIds, true)
                                        selectedIds = emptySet()
                                    }) {
                                        Icon(Icons.Filled.PushPin, contentDescription = "Fijar")
                                    }
                                }
                                if (anyPinned) {
                                    IconButton(onClick = {
                                        viewModel.setPinnedForIds(selectedIds, false)
                                        selectedIds = emptySet()
                                    }) {
                                        Icon(Icons.Outlined.PushPin, contentDescription = "Desfijar")
                                    }
                                }
                            }
                            if (viewMode == ViewMode.TRASH) {
                                IconButton(onClick = {
                                    viewModel.restoreIds(selectedIds)
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Filled.RestoreFromTrash, contentDescription = "Restaurar")
                                }
                                IconButton(onClick = {
                                    viewModel.deleteForeverIds(selectedIds)
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Filled.DeleteForever, contentDescription = "Eliminar para siempre")
                                }
                            } else {
                                IconButton(onClick = {
                                    if (settings.useTrash) {
                                        viewModel.moveToTrashIds(selectedIds)
                                    } else {
                                        viewModel.deleteForeverIds(selectedIds)
                                    }
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Borrar")
                                }
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = { BouncyPeach() },
                        navigationIcon = {
                            IconButton(
                                onClick = {
                                    // Mientras el drawer ya está animando (abriéndose o
                                    // cerrándose) ignoramos toques nuevos, para que un
                                    // doble-toque accidental (justo lo que describía el
                                    // reporte del bug) no dispare dos animaciones open()
                                    // superpuestas sobre el mismo drawerState.
                                    if (!drawerState.isAnimationRunning) {
                                        scope.launch { drawerState.open() }
                                    }
                                },
                                enabled = !drawerState.isAnimationRunning
                            ) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menú")
                            }
                        },
                        colors = if (settings.backgroundImagePath != null) {
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = settings.topBarOpacity)
                            )
                        } else {
                            TopAppBarDefaults.topAppBarColors()
                        }
                    )
                }
            },
            floatingActionButton = {
                if (!selectionMode && (viewMode == ViewMode.ALL || viewMode == ViewMode.PRIVATE)) {
                    val fabRotation by animateFloatAsState(
                        targetValue = if (fabExpanded) 45f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "fabRotation"
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn(animationSpec = tween(150)) +
                                scaleIn(animationSpec = tween(150), transformOrigin = TransformOrigin(1f, 1f)),
                            exit = fadeOut(animationSpec = tween(120)) +
                                scaleOut(animationSpec = tween(120), transformOrigin = TransformOrigin(1f, 1f))
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
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
                        }
                        FloatingActionButton(onClick = { fabExpanded = !fabExpanded }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "Nueva nota",
                                modifier = Modifier.graphicsLayer(rotationZ = fabRotation)
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
                    if (settings.noteLayout == NoteLayout.LIST) {
                        // Lista tradicional: una columna, tarjetas de ancho completo,
                        // sin el efecto "mampostería" (staggered) de la cuadrícula.
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    checkboxPosition = settings.checkboxPosition,
                                    selected = note.id in selectedIds,
                                    selectionMode = selectionMode,
                                    showFirstImage = settings.showFirstImage,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id
                                        } else {
                                            onNoteClick(note.id)
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = selectedIds + note.id
                                    },
                                    onTogglePin = { viewModel.togglePin(note) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalStaggeredGrid(
                            columns = StaggeredGridCells.Fixed(settings.gridColumns.coerceIn(1, 3)),
                            modifier = Modifier.fillMaxSize().padding(padding),
                            contentPadding = PaddingValues(8.dp),
                            verticalItemSpacing = 8.dp,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(notes, key = { it.id }) { note ->
                                NoteCard(
                                    note = note,
                                    checkboxPosition = settings.checkboxPosition,
                                    selected = note.id in selectedIds,
                                    selectionMode = selectionMode,
                                    showFirstImage = settings.showFirstImage,
                                    onClick = {
                                        if (selectionMode) {
                                            selectedIds = if (note.id in selectedIds) selectedIds - note.id else selectedIds + note.id
                                        } else {
                                            onNoteClick(note.id)
                                        }
                                    },
                                    onLongClick = {
                                        selectedIds = selectedIds + note.id
                                    },
                                    onTogglePin = { viewModel.togglePin(note) }
                                )
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
private fun BouncyPeach() {
    val scope = rememberCoroutineScope()
    // Antes había un único Animatable escalando X e Y a la vez: eso se ve
    // como un salto uniforme (crece y encoge igual en ambos ejes). Un rebote
    // "gelatinoso" de verdad aplasta primero (ancho > alto) y luego estira
    // (alto > alto normal, ancho < normal) mientras X e Y oscilan un poco
    // desfasados entre sí antes de asentarse: por eso van en dos Animatable
    // independientes con springs bien "bouncy", cada uno con su propia
    // secuencia de objetivos.
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }
    val jellySpring = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    Text(
        text = "\uD83C\uDF51",
        fontSize = 28.sp,
        modifier = Modifier
            .graphicsLayer(scaleX = scaleX.value, scaleY = scaleY.value)
            // El clip antes del clickable/indication ya no hace falta: quitamos
            // el ripple por completo (indication = null) porque el propio
            // rebote gelatinoso ya es el feedback visual del toque, y un
            // ripple encima competía con la deformación y además se veía
            // como un cuadrado feo sobre el emoji.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                scope.launch {
                    // Aplastar: se ensancha y se achata, como si absorbiera el toque.
                    launch {
                        scaleX.animateTo(1.35f, animationSpec = tween(70))
                        scaleX.animateTo(1f, animationSpec = jellySpring)
                    }
                    launch {
                        scaleY.animateTo(0.65f, animationSpec = tween(70))
                        scaleY.animateTo(1f, animationSpec = jellySpring)
                    }
                }
            }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    checkboxPosition: CheckboxPosition,
    selected: Boolean,
    selectionMode: Boolean,
    showFirstImage: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val context = LocalContext.current
    val bg = note.color?.let {
        runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull()
    } ?: MaterialTheme.colorScheme.surfaceContainerHigh
    val cardShape = RoundedCornerShape(16.dp)

    Box {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                // El clip DEBE ir antes que combinedClickable en la cadena de
                // modifiers: el ripple del clickable se dibuja con los límites
                // que tenga en ese punto de la cadena, y Card recién aplica su
                // propio recorte redondeado después (internamente, al pasar
                // "shape" a su Surface). Como el .clip() del Card llega más
                // tarde que el clickable, el ripple no quedaba recortado y se
                // veía como un cuadrado perfecto sobresaliendo de las esquinas
                // redondeadas. Con el clip acá, antes del clickable, el ripple
                // queda contenido dentro de la forma redondeada de la tarjeta.
                .clip(cardShape)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            // Transparente al 60% para que, si hay una imagen de fondo puesta, se
            // note a través de las tarjetas en vez de quedar tapada por completo.
            colors = CardDefaults.cardColors(containerColor = bg.copy(alpha = 0.6f)),
            shape = cardShape,
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = note.title.ifBlank { "(Sin título)" },
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (note.reminderAt != null) {
                    Icon(
                        Icons.Filled.Alarm,
                        contentDescription = "Tiene recordatorio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).padding(end = 4.dp)
                    )
                }
                Icon(
                    if (note.pinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (note.pinned) "Desfijar" else "Fijar",
                    tint = if (note.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).clickable(onClick = onTogglePin)
                )
            }
            Spacer(Modifier.height(4.dp))
            if (note.type == NoteType.CHECKLIST) {
                note.checklistItems.take(4).forEach { item ->
                    val symbol = if (item.checked) "☑" else "☐"
                    val text = if (checkboxPosition == CheckboxPosition.START) {
                        "$symbol ${item.text}"
                    } else {
                        "${item.text} $symbol"
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (note.checklistItems.size > 4) {
                    Text("+${note.checklistItems.size - 4} más", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                val allParts = parseNoteContent(note.content)
                val firstImage = if (showFirstImage) {
                    allParts.filterIsInstance<ContentPart.ImagePart>().firstOrNull()
                } else null

                if (firstImage != null) {
                    // El usuario pidió poder forzar que la miniatura de la nota
                    // sea siempre su primera imagen, incluso si hay mucho texto
                    // antes que normalmente "gastaría" el presupuesto de la
                    // tarjeta antes de llegar a ella. La mostramos primero,
                    // aparte del recorrido de abajo (que se salta esta misma
                    // imagen para no repetirla).
                    AsyncImage(
                        model = File(ImageStorage.imagesDir(context), firstImage.fileName),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // Recorre el contenido en su orden real (texto e imágenes intercalados),
                // en vez de agrupar todo el texto junto y la imagen aparte.
                var budget = 6
                var skippedFirstImage = false
                for (part in allParts) {
                    if (budget <= 0) break
                    if (firstImage != null && !skippedFirstImage && part === firstImage) {
                        skippedFirstImage = true
                        continue
                    }
                    when (part) {
                        is ContentPart.ImagePart -> {
                            AsyncImage(
                                model = File(ImageStorage.imagesDir(context), part.fileName),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            )
                            Spacer(Modifier.height(6.dp))
                            budget -= 3
                        }
                        is ContentPart.TextPart -> {
                            val cleaned = stripFormattingMarkers(part.text).trim()
                            if (cleaned.isNotEmpty()) {
                                Text(
                                    text = cleaned,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                budget -= 3
                                // Si este bloque de texto tenía más de 3 líneas, ya lo
                                // cortamos con "...". Mostrar después una imagen que
                                // está más abajo en la nota (a veces mucho más abajo)
                                // da la impresión de que la nota "es" esa imagen, cuando
                                // en realidad la nota es sobre todo texto. Frenamos acá
                                // en vez de seguir con las partes siguientes.
                                if (cleaned.lines().size > 3) budget = 0
                            }
                        }
                    }
                }
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
        if (selectionMode) {
            Icon(
                if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (selected) "Seleccionada" else "No seleccionada",
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(22.dp)
                    .background(Color.Black.copy(alpha = 0.35f), CircleShape)
                    .padding(2.dp)
            )
        }
    }
}
