package com.example.notes.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.notes.data.AppSettings
import com.example.notes.data.BackupManager
import com.example.notes.data.CheckboxPosition
import com.example.notes.data.FontScale
import com.example.notes.data.SortOrder
import com.example.notes.data.StartView
import com.example.notes.data.ThemeMode
import com.example.notes.ui.theme.ThemeSeedColors
import kotlinx.coroutines.launch

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
            item { SectionTitle("Apariencia") }
            item {
                ChipSetting(
                    label = "Tema",
                    options = listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro"),
                    selected = settings.themeMode,
                    onSelect = { v -> onUpdate { it.copy(themeMode = v) } }
                )
            }
            item {
                SwitchSetting(
                    label = "Color dinámico (Material You)",
                    checked = settings.dynamicColor,
                    onCheckedChange = { v -> onUpdate { it.copy(dynamicColor = v) } }
                )
            }
            if (!settings.dynamicColor) {
                item {
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
            }
            item {
                DiscreteSlider(
                    label = "Columnas del grid",
                    values = listOf(1, 2, 3),
                    valueLabels = listOf("1", "2", "3"),
                    selected = settings.gridColumns,
                    onSelect = { v -> onUpdate { it.copy(gridColumns = v) } }
                )
            }
            item {
                ChipSetting(
                    label = "Tamaño de texto",
                    options = listOf(FontScale.SMALL to "Chico", FontScale.MEDIUM to "Mediano", FontScale.LARGE to "Grande"),
                    selected = settings.fontScale,
                    onSelect = { v -> onUpdate { it.copy(fontScale = v) } }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            item { SectionTitle("Comportamiento") }
            item {
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
            }
            item {
                ChipSetting(
                    label = "Posición de la casilla en checklists",
                    options = listOf(CheckboxPosition.START to "Antes del texto", CheckboxPosition.END to "Después del texto"),
                    selected = settings.checkboxPosition,
                    onSelect = { v -> onUpdate { it.copy(checkboxPosition = v) } }
                )
            }
            item {
                SwitchSetting(
                    label = "Enviar tareas marcadas al final",
                    checked = settings.autoSortChecked,
                    onCheckedChange = { v -> onUpdate { it.copy(autoSortChecked = v) } }
                )
            }
            item {
                SwitchSetting(
                    label = "Confirmar antes de borrar",
                    checked = settings.confirmBeforeDelete,
                    onCheckedChange = { v -> onUpdate { it.copy(confirmBeforeDelete = v) } }
                )
            }
            item {
                SwitchSetting(
                    label = "Doble toque para editar (modo lectura)",
                    checked = settings.doubleTapToEdit,
                    onCheckedChange = { v -> onUpdate { it.copy(doubleTapToEdit = v) } }
                )
            }
            item {
                ChipSetting(
                    label = "Vista al abrir la app",
                    options = listOf(StartView.ALL to "Todas las notas", StartView.LAST_USED to "Última vista usada"),
                    selected = settings.startView,
                    onSelect = { v -> onUpdate { it.copy(startView = v) } }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            item { SectionTitle("Privacidad y seguridad") }
            item {
                DiscreteSlider(
                    label = "Recordar desbloqueo biométrico",
                    values = listOf(0, 5, 15, 30, 60, -1),
                    valueLabels = listOf("Siempre pedir", "5 min", "15 min", "30 min", "1 hora", "Hasta cerrar la app"),
                    selected = settings.biometricRememberMinutes,
                    onSelect = { v -> onUpdate { it.copy(biometricRememberMinutes = v) } }
                )
            }
            item {
                SwitchSetting(
                    label = "Ocultar contenido en apps recientes",
                    checked = settings.hideFromRecents,
                    onCheckedChange = { v -> onUpdate { it.copy(hideFromRecents = v) } }
                )
            }
            item {
                SwitchSetting(
                    label = "Bloqueo biométrico para toda la app",
                    checked = settings.appWideBiometricLock,
                    onCheckedChange = { v -> onUpdate { it.copy(appWideBiometricLock = v) } }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            item { SectionTitle("Papelera") }
            item {
                DiscreteSlider(
                    label = "Purgar notas borradas después de",
                    values = listOf(7, 14, 30, 60, 90, -1),
                    valueLabels = listOf("7 días", "14 días", "30 días", "60 días", "90 días", "Nunca"),
                    selected = settings.trashPurgeDays,
                    onSelect = { v -> onUpdate { it.copy(trashPurgeDays = v) } }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            item { SectionTitle("Imágenes") }
            item {
                SwitchSetting(
                    label = "Comprimir imágenes al guardar",
                    checked = settings.compressImages,
                    onCheckedChange = { v -> onUpdate { it.copy(compressImages = v) } }
                )
            }
            if (settings.compressImages) {
                item {
                    DiscreteSlider(
                        label = "Calidad de compresión",
                        values = listOf(50, 65, 80, 90, 100),
                        valueLabels = listOf("50%", "65%", "80%", "90%", "100%"),
                        selected = settings.imageQuality,
                        onSelect = { v -> onUpdate { it.copy(imageQuality = v) } }
                    )
                }
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp)) }
            item { SectionTitle("Datos") }
            item {
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
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
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

@Composable
private fun <T> ChipSetting(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
