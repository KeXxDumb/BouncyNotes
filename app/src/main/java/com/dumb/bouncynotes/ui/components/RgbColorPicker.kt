package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Selector de color libre por canales R, G, B (en vez de una paleta fija).
@Composable
fun RgbColorPicker(selectedHex: String?, onColorChange: (String?) -> Unit) {
    // OJO: antes esto era `remember(selectedHex)`. Cada vez que se movía un
    // slider, emit() llamaba a onColorChange -> el padre actualizaba su
    // estado -> selectedHex cambiaba -> remember(selectedHex) recreaba r/g/b
    // desde cero. Eso pasaba en CADA evento de arrastre (docenas de veces
    // por segundo), así que el slider competía consigo mismo durante el
    // arrastre y se sentía trabado/saltón ("no funciona"). Ahora el estado
    // interno se inicializa una sola vez; solo se resincroniza si el color
    // recibido cambia por una fuente EXTERNA a este picker (por ejemplo, al
    // abrir una nota distinta), usando su propio valor recordado como
    // referencia en vez de recrearse en cada emisión propia.
    var lastEmittedHex by remember { mutableStateOf(selectedHex) }
    val parsed = selectedHex?.let { runCatching { android.graphics.Color.parseColor(it) }.getOrNull() }
    var r by remember { mutableStateOf(if (parsed != null) android.graphics.Color.red(parsed) else 239) }
    var g by remember { mutableStateOf(if (parsed != null) android.graphics.Color.green(parsed) else 235) }
    var b by remember { mutableStateOf(if (parsed != null) android.graphics.Color.blue(parsed) else 233) }

    if (selectedHex != lastEmittedHex) {
        // Cambio externo real (no producido por nuestros propios sliders):
        // resincronizamos.
        lastEmittedHex = selectedHex
        if (parsed != null) {
            r = android.graphics.Color.red(parsed)
            g = android.graphics.Color.green(parsed)
            b = android.graphics.Color.blue(parsed)
        }
    }

    fun emit() {
        val hex = String.format("#%02X%02X%02X", r, g, b)
        lastEmittedHex = hex
        onColorChange(hex)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(r, g, b))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = { onColorChange(null) }) {
                Text("Sin color")
            }
        }
        ChannelSlider(label = "R", value = r, tint = Color(0xFFE53935)) { r = it; emit() }
        ChannelSlider(label = "G", value = g, tint = Color(0xFF43A047)) { g = it; emit() }
        ChannelSlider(label = "B", value = b, tint = Color(0xFF1E88E5)) { b = it; emit() }
    }
}

@Composable
private fun ChannelSlider(label: String, value: Int, tint: Color, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, modifier = Modifier.width(20.dp))
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = tint, activeTrackColor = tint)
        )
        Text(text = value.toString(), modifier = Modifier.width(36.dp))
    }
}
