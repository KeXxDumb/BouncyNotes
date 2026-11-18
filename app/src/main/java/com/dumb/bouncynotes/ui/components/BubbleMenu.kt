package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

// Burbuja con opciones que APUNTA al botón que la abrió (una flechita hacia
// abajo, alineada con el centro del botón). Reemplaza al DropdownMenu de
// Material, que en la barra inferior aparecía bastante más arriba del botón (su
// posicionamiento deja un margen vertical grande al reubicarse para no salirse
// de la pantalla) y no se parecía al resto de la app. Mismo estilo que la barra
// inferior: tinte oscuro, borde blanco sutil y esquinas redondeadas.
//
// Se declara DENTRO de un contenedor que envuelve al botón (por ejemplo un Box):
// los límites de ese contenedor son el "ancla" sobre la que se posiciona.
private val BubbleColor = Color(0xFF16151A).copy(alpha = 0.94f)

@Composable
internal fun BubbleMenu(onDismissRequest: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val density = LocalDensity.current
    val gapPx = with(density) { 4.dp.roundToPx() }
    val marginPx = with(density) { 8.dp.roundToPx() }
    val arrowWidth = 18.dp
    val arrowHeight = 9.dp
    val arrowWidthPx = with(density) { arrowWidth.roundToPx() }
    // Posición horizontal (dentro de la burbuja) del centro del botón: la
    // burbuja se pega al borde de la pantalla si el botón está en un extremo, y
    // entonces la flecha ya no queda en el medio de ella. -1 = todavía sin medir.
    var arrowCenterPx by remember { mutableIntStateOf(-1) }

    val provider = remember(gapPx, marginPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val anchorCenterX = anchorBounds.left + anchorBounds.width / 2
                val maxX = maxOf(marginPx, windowSize.width - popupContentSize.width - marginPx)
                val x = (anchorCenterX - popupContentSize.width / 2).coerceIn(marginPx, maxX)
                val y = anchorBounds.top - popupContentSize.height - gapPx
                arrowCenterPx = anchorCenterX - x
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = provider,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Column {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .widthIn(min = 180.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(BubbleColor)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
                    .padding(vertical = 6.dp),
                content = content
            )
            if (arrowCenterPx >= 0) {
                Canvas(
                    modifier = Modifier
                        .offset { IntOffset(arrowCenterPx - arrowWidthPx / 2, 0) }
                        .size(arrowWidth, arrowHeight)
                ) {
                    val path = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(size.width, 0f)
                        lineTo(size.width / 2f, size.height)
                        close()
                    }
                    drawPath(path, BubbleColor)
                }
            }
        }
    }
}

// Opción de una BubbleMenu: ícono opcional, texto y una marca si está elegida.
@Composable
internal fun BubbleItem(
    text: String,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(alpha = 0.95f), modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text,
            color = Color.White.copy(alpha = 0.95f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                Icons.Filled.Check,
                contentDescription = "Elegida",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
