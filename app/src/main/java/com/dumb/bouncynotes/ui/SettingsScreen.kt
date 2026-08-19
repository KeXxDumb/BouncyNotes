package com.dumb.bouncynotes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dumb.bouncynotes.R
import com.dumb.bouncynotes.data.AppIcon
import com.dumb.bouncynotes.data.AppIconManager
import com.dumb.bouncynotes.data.AppSettings
import com.dumb.bouncynotes.data.BackupManager
import com.dumb.bouncynotes.data.CheckboxPosition
import com.dumb.bouncynotes.data.FontScale
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.SortOrder
import com.dumb.bouncynotes.data.StartView
import com.dumb.bouncynotes.data.ThemeMode
import com.dumb.bouncynotes.ui.theme.ThemeSeedColors
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    noteViewModel: NoteViewModel,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showDisableTrashWarning by remember { mutableStateOf(false) }
    var trashedCount by remember { mutableStateOf(0) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val notes = noteViewModel.getAllNotesSnapshot()
                val ok = BackupManager.exportNotes(context, uri, notes)
                statusMessage = if (ok) "Notas exportadas correctamente" else "No se pudo exportar"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val imported = BackupManager.importNotes(context, uri)
                noteViewModel.importNotes(imported)
                statusMessage = "${imported.size} notas importadas"
            }
        }
    }

    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = ImageStorage.copyFromUri(context, uri)
            if (fileName != null) {
                onUpdate { it.copy(backgroundImagePath = fileName) }
            }
        }
    }

    if (showDisableTrashWarning) {
        AlertDialog(
            onDismissRequest = { showDisableTrashWarning = false },
            title = { Text("¿Desactivar la papelera?") },
            text = {
                Text(
                    if (trashedCount > 0) {
                        "Esto eliminará PARA SIEMPRE las $trashedCount notas que están actualmente en la papelera. Esta acción no se puede deshacer."
                    } else {
                        "A partir de ahora, borrar una nota la eliminará de inmediato sin poder recuperarla."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    noteViewModel.deleteAllTrashed()
                    onUpdate { it.copy(useTrash = false) }
                    showDisableTrashWarning = false
                }) { Text("Desactivar") }
            },
            dismissButton = {
                TextButton(onClick = { showDisableTrashWarning = false }) { Text("Cancelar") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajustes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ExpandableSection(title = "Apariencia", icon = Icons.Filled.Palette) {
                    ChipSetting(
                        label = "Tema",
                        options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro"),
                        selected = settings.themeMode,
                        onSelect = { v -> onUpdate { it.copy(themeMode = v) } }
                    )
                    SwitchSetting(
                        label = "Color dinámico (Material You)",
                        checked = settings.dynamicColor,
                        onCheckedChange = { v -> onUpdate { it.copy(dynamicColor = v) } }
                    )
                    if (!settings.dynamicColor) {
                        Text("Color del tema", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                            ThemeSeedColors.forEach { hex ->
                                val c = runCatching { Color(android.graphics.Color.parseColor(hex)) }
                                    .getOrDefault(MaterialTheme.colorScheme.primary)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(c, CircleShape)
                                        .border(
                                            width = if (settings.seedColorHex == hex) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable { onUpdate { s -> s.copy(seedColorHex = hex) } }
                                )
                            }
                        }
                    }
                    DiscreteSlider(
                        label = "Columnas del grid",
                        values = listOf(1, 2, 3),
                        valueLabels = listOf("1", "2", "3"),
                        selected = settings.gridColumns,
                        onSelect = { v -> onUpdate { it.copy(gridColumns = v) } }
                    )
                    ChipSetting(
                        label = "Tamaño de texto",
                        options = listOf(FontScale.SMALL to "Chico", FontScale.MEDIUM to "Mediano", FontScale.LARGE to "Grande"),
                        selected = settings.fontScale,
                        onSelect = { v -> onUpdate { it.copy(fontScale = v) } }
                    )

                    Text("Ícono de la app", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(6.dp))
                    AppIconSetting()

                    Text("Imagen de fondo", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                    Spacer(Modifier.height(6.dp))
                    if (settings.backgroundImagePath != null) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                            AsyncImage(
                                model = File(ImageStorage.imagesDir(context), settings.backgroundImagePath),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                            )
                            Spacer(Modifier.width(12.dp))
                            TextButton(onClick = { onUpdate { it.copy(backgroundImagePath = null) } }) {
                                Text("Quitar imagen")
                            }
                        }
                        SwitchSetting(
                            label = "Monocromática (usa el color del tema)",
                            checked = settings.backgroundMonochrome,
                            onCheckedChange = { v -> onUpdate { it.copy(backgroundMonochrome = v) } }
                        )
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Opacidad de la imagen: ${(settings.backgroundImageOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = settings.backgroundImageOpacity,
                                onValueChange = { v -> onUpdate { it.copy(backgroundImageOpacity = v) } },
                                valueRange = 0f..1f
                            )
                        }
                        SwitchSetting(
                            label = "Desvanecer bordes",
                            checked = settings.backgroundFade,
                            onCheckedChange = { v -> onUpdate { it.copy(backgroundFade = v) } }
                        )
                        if (settings.backgroundFade) {
                            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    "Opacidad del desvanecido: ${(settings.backgroundFadeOpacity * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Slider(
                                    value = settings.backgroundFadeOpacity,
                                    onValueChange = { v -> onUpdate { it.copy(backgroundFadeOpacity = v) } },
                                    valueRange = 0f..1f
                                )
                            }
                        }
                        // Con imagen de fondo, la barra de título sólida tapaba parte de
                        // la imagen y desentonaba con el resto de la pantalla (que sí
                        // deja ver el fondo). La hacemos semitransparente para que
                        // combine, con opacidad ajustable.
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Opacidad de la barra de título: ${(settings.topBarOpacity * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = settings.topBarOpacity,
                                onValueChange = { v -> onUpdate { it.copy(topBarOpacity = v) } },
                                valueRange = 0f..1f
                            )
                        }
                    } else {
                        OutlinedButton(onClick = {
                            backgroundImageLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Text("Elegir imagen de fondo")
                        }
                    }
                }
            }

            item {
                ExpandableSection(title = "Comportamiento", icon = Icons.Filled.Tune) {
                    ChipSetting(
                        label = "Ordenar notas por",
                        options = listOf(
                            SortOrder.UPDATED to "Última edición",
                            SortOrder.CREATED to "Creación",
                            SortOrder.ALPHABETICAL to "Alfabético",
                            SortOrder.COLOR to "Color"
                        ),
                        selected = settings.sortOrder,
                        onSelect = { v -> onUpdate { it.copy(sortOrder = v) } }
                    )
                    ChipSetting(
                        label = "Posición de la casilla en checklists",
                        options = listOf(CheckboxPosition.START to "Antes del texto", CheckboxPosition.END to "Después del texto"),
                        selected = settings.checkboxPosition,
                        onSelect = { v -> onUpdate { it.copy(checkboxPosition = v) } }
                    )
                    SwitchSetting(
                        label = "Enviar tareas marcadas al final",
                        checked = settings.autoSortChecked,
                        onCheckedChange = { v -> onUpdate { it.copy(autoSortChecked = v) } }
                    )
                    SwitchSetting(
                        label = "Confirmar antes de borrar",
                        checked = settings.confirmBeforeDelete,
                        onCheckedChange = { v -> onUpdate { it.copy(confirmBeforeDelete = v) } }
                    )
                    SwitchSetting(
                        label = "Doble toque para editar (modo lectura)",
                        checked = settings.doubleTapToEdit,
                        onCheckedChange = { v -> onUpdate { it.copy(doubleTapToEdit = v) } }
                    )
                    ChipSetting(
                        label = "Vista al abrir la app",
                        options = listOf(StartView.ALL to "Todas las notas", StartView.LAST_USED to "Última vista usada"),
                        selected = settings.startView,
                        onSelect = { v -> onUpdate { it.copy(startView = v) } }
                    )
                }
            }

            item {
                ExpandableSection(title = "Privacidad y seguridad", icon = Icons.Filled.Security) {
                    DiscreteSlider(
                        label = "Recordar desbloqueo biométrico",
                        values = listOf(0, 5, 15, 30, 60, -1),
                        valueLabels = listOf("Siempre pedir", "5 min", "15 min", "30 min", "1 hora", "Hasta cerrar la app"),
                        selected = settings.biometricRememberMinutes,
                        onSelect = { v -> onUpdate { it.copy(biometricRememberMinutes = v) } }
                    )
                    SwitchSetting(
                        label = "Ocultar contenido en apps recientes",
                        checked = settings.hideFromRecents,
                        onCheckedChange = { v -> onUpdate { it.copy(hideFromRecents = v) } }
                    )
                    SwitchSetting(
                        label = "Bloqueo biométrico para toda la app",
                        checked = settings.appWideBiometricLock,
                        onCheckedChange = { v -> onUpdate { it.copy(appWideBiometricLock = v) } }
                    )
                }
            }

            item {
                ExpandableSection(title = "Papelera", icon = Icons.Filled.Delete) {
                    SwitchSetting(
                        label = "Usar papelera",
                        checked = settings.useTrash,
                        onCheckedChange = { v ->
                            if (!v) {
                                scope.launch {
                                    trashedCount = noteViewModel.getTrashedCount()
                                    showDisableTrashWarning = true
                                }
                            } else {
                                onUpdate { it.copy(useTrash = true) }
                            }
                        }
                    )
                    if (settings.useTrash) {
                        DiscreteSlider(
                            label = "Purgar notas borradas después de",
                            values = listOf(7, 14, 30, 60, 90, -1),
                            valueLabels = listOf("7 días", "14 días", "30 días", "60 días", "90 días", "Nunca"),
                            selected = settings.trashPurgeDays,
                            onSelect = { v -> onUpdate { it.copy(trashPurgeDays = v) } }
                        )
                    } else {
                        Text(
                            "Al borrar una nota se eliminará de inmediato, sin poder recuperarla.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            item {
                ExpandableSection(title = "Imágenes", icon = Icons.Filled.Image) {
                    SwitchSetting(
                        label = "Comprimir imágenes al guardar",
                        checked = settings.compressImages,
                        onCheckedChange = { v -> onUpdate { it.copy(compressImages = v) } }
                    )
                    if (settings.compressImages) {
                        DiscreteSlider(
                            label = "Calidad de compresión",
                            values = listOf(50, 65, 80, 90, 100),
                            valueLabels = listOf("50%", "65%", "80%", "90%", "100%"),
                            selected = settings.imageQuality,
                            onSelect = { v -> onUpdate { it.copy(imageQuality = v) } }
                        )
                    }
                }
            }

            item {
                ExpandableSection(title = "Datos", icon = Icons.Filled.CloudUpload) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = { exportLauncher.launch("notas_respaldo.zip") }) {
                            Text("Exportar notas")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip")) }) {
                            Text("Importar notas")
                        }
                    }
                    statusMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
            item {
                Text(
                    text = "Bouncy Notes 🍑 — hecho por KeXxDumb",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Contraer" else "Expandir"
                )
            }
            if (expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// Elegir entre los íconos de la app disponibles (activity-alias en el
// manifiesto). El estado real vive en PackageManager, no en nuestro
// DataStore, así que lo leemos directo de ahí al entrar a esta pantalla.
@Composable
private fun AppIconSetting() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(AppIconManager.current(context)) }
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        AppIcon.entries.forEach { icon ->
            val isSelected = icon == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        AppIconManager.setIcon(context, icon)
                        selected = icon
                    }
                    .padding(10.dp)
            ) {
                if (icon == AppIcon.NOTE_GIRL) {
                    Image(
                        painter = painterResource(R.drawable.ic_notegirl_background),
                        contentDescription = icon.label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFB74D)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍑", style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(icon.label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text(
        "El lanzador del teléfono puede tardar un momento en mostrarlo, o pedir " +
            "reabrir el cajón de apps. En algunos casos el acceso directo de la " +
            "pantalla de inicio hay que sacarlo y volver a agregarlo para que " +
            "tome el ícono nuevo.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipSetting(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        // Antes era un Row: con 4 opciones ("Ordenar notas por") no entraban en el
        // ancho de la pantalla y el último chip ("Color") se comprimía hasta quedar
        // angosto y con el texto vertical. FlowRow los pasa a una segunda línea.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { (value, text) ->
                FilterChip(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    label = { Text(text) }
                )
            }
        }
    }
}

@Composable
private fun DiscreteSlider(
    label: String,
    values: List<Int>,
    valueLabels: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val index = values.indexOf(selected).let { if (it == -1) 0 else it }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("$label: ${valueLabels.getOrElse(index) { "" }}", style = MaterialTheme.typography.labelLarge)
        Slider(
            value = index.toFloat(),
            onValueChange = { newValue ->
                val newIndex = newValue.toInt().coerceIn(0, values.size - 1)
                onSelect(values[newIndex])
            },
            valueRange = 0f..(values.size - 1).toFloat(),
            steps = (values.size - 2).coerceAtLeast(0)
        )
    }
}
