package com.dumb.bouncynotes.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dumb.bouncynotes.data.ThemeMode

// Compartido por PinnedNoteWidgetConfigActivity (que además elige una nota)
// y WidgetAppearanceConfigActivity (que solo configura esto) — para no
// duplicar la misma UI de tema/fondo en dos lugares.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetAppearancePicker(
    themeMode: ThemeMode,
    backgroundMode: WidgetBackgroundMode,
    onThemeChange: (ThemeMode) -> Unit,
    onBackgroundChange: (WidgetBackgroundMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Apariencia del widget", style = MaterialTheme.typography.titleMedium)

        Text(
            "Tema",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(ThemeMode.SYSTEM to "Sistema", ThemeMode.LIGHT to "Claro", ThemeMode.DARK to "Oscuro").forEach { (mode, label) ->
                FilterChip(
                    selected = themeMode == mode,
                    onClick = { onThemeChange(mode) },
                    label = { Text(label) }
                )
            }
        }

        Text(
            "Fondo",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                WidgetBackgroundMode.SOLID to "Sólido",
                WidgetBackgroundMode.TRANSLUCENT to "Transparente",
                WidgetBackgroundMode.INVISIBLE to "Invisible"
            ).forEach { (mode, label) ->
                FilterChip(
                    selected = backgroundMode == mode,
                    onClick = { onBackgroundChange(mode) },
                    label = { Text(label) }
                )
            }
        }
        // "Transparente" (pedido) usa el MISMO color del tema elegido
        // arriba, con poca opacidad — no queda invisible del todo, solo se
        // transparenta un poco el wallpaper de atrás. "Invisible" si es
        // 0% del todo.
        Text(
            "\"Transparente\" mantiene el color del tema elegido arriba, solo con menos opacidad. \"Invisible\" saca el fondo del todo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
