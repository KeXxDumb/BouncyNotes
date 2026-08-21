package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.GalleryLayout
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.parseNoteContent
import java.io.File

// Renderiza el contenido de una nota de texto en modo lectura: texto, imágenes
// sueltas y grupos de imágenes intercalados exactamente en la posición donde
// el usuario los insertó.
@Composable
fun NoteContentView(content: String, onImageClick: (Int) -> Unit) {
    val context = LocalContext.current
    val parts = parseNoteContent(content)
    // Cuenta imágenes en la lista PLANA (sueltas + las de dentro de cada
    // grupo, en orden), la misma indexación que usa extractImageFileNames y
    // por lo tanto el visor a pantalla completa.
    var imageOccurrence = 0

    Column {
        parts.forEach { part ->
            when (part) {
                is ContentPart.TextPart -> {
                    if (part.text.isNotBlank()) {
                        InlineMarkdownText(text = part.text)
                    }
                }
                is ContentPart.ImagePart -> {
                    val occurrenceIndex = imageOccurrence
                    imageOccurrence++
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // El pellizco (detectTransformGestures) que había antes
                        // capturaba TODO el toque sobre la imagen, incluido un
                        // arrastre de un solo dedo, así que bloqueaba el scroll de la
                        // nota cuando el deslizamiento arrancaba justo sobre una
                        // imagen, y además el pellizco en sí no siempre se detectaba
                        // bien. Un tap simple con .clickable convive bien con el
                        // scroll (un arrastre se le \"escapa\" al contenedor que
                        // scrollea) y abre el visor a pantalla completa, que ya tiene
                        // su propio zoom/pan y sigue siendo de solo lectura (sin
                        // edición de descripción).
                        AsyncImage(
                            model = File(ImageStorage.imagesDir(context), part.fileName),
                            contentDescription = part.caption.ifBlank { "Imagen" },
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onImageClick(occurrenceIndex) }
                        )
                        if (part.caption.isNotBlank()) {
                            Text(
                                text = part.caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
                is ContentPart.GalleryPart -> {
                    // El primer índice de este grupo dentro de la lista plana de
                    // imágenes; cada imagen del grupo es startIndex + su
                    // posición dentro de part.fileNames.
                    val startIndex = imageOccurrence
                    imageOccurrence += part.fileNames.size
                    GalleryGrid(
                        layout = part.layout,
                        fileNames = part.fileNames,
                        onImageClick = { indexInGroup -> onImageClick(startIndex + indexInGroup) }
                    )
                }
            }
        }
    }
}

// Cuadrícula (o carrusel) para un grupo de imágenes agrupadas. El formato
// (GalleryLayout) se elige al insertar el grupo, o toma el que esté
// configurado por defecto en Ajustes.
@Composable
private fun GalleryGrid(layout: GalleryLayout, fileNames: List<String>, onImageClick: (Int) -> Unit) {
    val context = LocalContext.current
    when (layout) {
        GalleryLayout.CAROUSEL -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp)
            ) {
                fileNames.forEachIndexed { index, fileName ->
                    AsyncImage(
                        model = File(ImageStorage.imagesDir(context), fileName),
                        contentDescription = "Imagen ${index + 1} de ${fileNames.size}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(150.dp)
                            .padding(end = if (index != fileNames.lastIndex) 6.dp else 0.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onImageClick(index) }
                    )
                }
            }
        }
        GalleryLayout.GRID_2, GalleryLayout.GRID_3 -> {
            val columns = if (layout == GalleryLayout.GRID_2) 2 else 3
            // No hace falta LazyVerticalGrid (con scroll propio) para un puñado
            // de miniaturas fijas: alcanza con filas de Row, y así el grupo
            // scrollea junto con el resto de la nota en vez de tener su propio
            // scroll anidado (que además Compose no permite fácil dentro de
            // otra columna con scroll).
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                fileNames.chunked(columns).forEachIndexed { rowIndex, rowFiles ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        rowFiles.forEachIndexed { colIndex, fileName ->
                            val globalIndex = rowIndex * columns + colIndex
                            AsyncImage(
                                model = File(ImageStorage.imagesDir(context), fileName),
                                contentDescription = "Imagen ${globalIndex + 1} de ${fileNames.size}",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .weight(1f)
                                    .size(if (columns == 2) 150.dp else 100.dp)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onImageClick(globalIndex) }
                            )
                        }
                        // Si la última fila queda incompleta, rellenamos con
                        // espacio vacío para que las celdas no se estiren de más.
                        repeat(columns - rowFiles.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
