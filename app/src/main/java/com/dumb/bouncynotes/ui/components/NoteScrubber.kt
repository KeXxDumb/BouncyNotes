package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// Reemplaza al botón de "ir al final" — en vez de un único destino (el
// final), permite saltar a CUALQUIER punto de la nota arrastrando, como el
// scrollbar de un lector o el "fast scroll" de una lista larga de contactos.
// No sabe nada de LazyColumn/ScrollState/checklist — solo recibe un
// progreso 0f..1f y avisa hacia dónde se está arrastrando; quien lo llama
// (NoteEditScreen) decide cómo traducir eso a un scroll real, porque esa
// traducción es distinta según el tipo de contenido (ver el comentario en
// el call site sobre requestScrollToItem vs dispatchRawDelta).
@Composable
fun NoteScrubber(
    progress: Float,
    onScrub: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var trackHeightPx by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbHeightDp = 26.dp
    val thumbHeightPx = with(density) { thumbHeightDp.toPx() }

    Box(
        modifier = modifier
            .width(18.dp)
            .onGloballyPositioned { trackHeightPx = it.size.height.toFloat() }
            .pointerInputScrub(
                onStart = { y ->
                    isDragging = true
                    if (trackHeightPx > 0f) onScrub((y / trackHeightPx).coerceIn(0f, 1f))
                },
                onDrag = { y ->
                    if (trackHeightPx > 0f) onScrub((y / trackHeightPx).coerceIn(0f, 1f))
                },
                onEnd = { isDragging = false }
            )
    ) {
        // Riel: una línea vertical fina, apenas visible, de punta a punta.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(1.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
        )
        // Pulgar: se mueve entre el tope y el fondo del riel según progress,
        // dejando lugar para su propio alto (si no, en progress=1 se saldría
        // del riel por abajo).
        val thumbOffsetPx = (progress.coerceIn(0f, 1f) * (trackHeightPx - thumbHeightPx).coerceAtLeast(0f))
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset { IntOffset(0, thumbOffsetPx.roundToInt()) }
                .size(width = 12.dp, height = thumbHeightDp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    if (isDragging) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
        )
        // Solo mientras se arrastra: un globito con el % de avance, para que
        // arrastrar a ciegas tenga algo de referencia de "cuánto falta".
        if (isDragging) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(-(with(density) { 46.dp.toPx() }).roundToInt(), thumbOffsetPx.roundToInt() - 4) }
                    .size(width = 38.dp, height = thumbHeightDp + 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.75f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${(progress.coerceIn(0f, 1f) * 100).roundToInt()}%",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

// Envuelve detectVerticalDragGestures para exponer tanto la posición inicial
// del toque (onStart, que detectVerticalDragGestures normalmente no separa
// de onDragStart con offset) como cada punto del arrastre — así un simple
// toque (sin arrastrar más) también salta a ese punto, no solo un arrastre
// de verdad.
private fun Modifier.pointerInputScrub(
    onStart: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: () -> Unit
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = { offset -> onStart(offset.y) },
            onDragEnd = { onEnd() },
            onDragCancel = { onEnd() },
            onVerticalDrag = { change, _ ->
                change.consume()
                onDrag(change.position.y)
            }
        )
    }
)
