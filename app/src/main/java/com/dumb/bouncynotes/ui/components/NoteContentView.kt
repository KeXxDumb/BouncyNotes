package com.dumb.bouncynotes.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
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
import android.content.Context
import coil.compose.AsyncImage
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.GalleryLayout
import com.dumb.bouncynotes.data.ImageStorage
import com.dumb.bouncynotes.data.parseNoteContent
import java.io.File

// Aporta los ítems del contenido de una nota de texto en modo lectura
// (texto, imágenes sueltas y grupos, intercalados en su posición real)
// directo al LazyListScope de una LazyColumn externa — no es una Column
// propia, así que el que la llama controla el scroll (y por lo tanto puede
// scrollear TODO junto con el resto de la pantalla, sin un scroll anidado).
//
// IMPORTANTE: esto NO puede ser @Composable. El bloque de contenido de un
// LazyColumn { ... } (el LazyListScope.() -> Unit) NO es en sí mismo un
// contexto @Composable — ahí adentro solo se pueden llamar funciones
// normales como item()/items() (que reciben un lambda composable aparte
// para el contenido real de cada fila). Marcar esta función @Composable y
// llamarla suelta dentro de un LazyColumn{} rompe la compilación
// ("@Composable invocations can only happen from the context of a
// @Composable function"). Por la misma razón, no se puede leer
// LocalContext.current acá adentro (esa lectura sí necesita contexto
// composable) — context llega como parámetro, provisto por quien SÍ está
// en un @Composable (la pantalla que arma el LazyColumn).
fun LazyListScope.NoteContentView(context: Context, content: String, onImageClick: (Int) -> Unit) {
    val parts = parseNoteContent(content)
    // Cuenta imágenes en la lista PLANA (sueltas + las de dentro de cada
    // grupo, en orden), la misma indexación que usa extractImageFileNames y
    // por lo tanto el visor a pantalla completa.
    var imageOccurrence = 0

    parts.forEachIndexed { partIndex, part ->
        when (part) {
            is ContentPart.TextPart -> {
                if (part.text.isNotBlank()) {
                    item(key = "text-$partIndex") {
                        InlineMarkdownText(text = part.text)
                    }
                }
            }
            is ContentPart.ImagePart -> {
                val occurrenceIndex = imageOccurrence
                imageOccurrence++
                item(key = "image-$partIndex") {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        // Se usa la relación de aspecto REAL de la imagen (leída
                        // aparte, solo el encabezado del archivo — ver
                        // rememberImageAspectRatio) para que LazyColumn sepa el
                        // alto correcto ANTES de que Coil termine de cargarla del
                        // todo, sin tener que recortarla a una proporción fija.
                        val aspectRatio = rememberImageAspectRatio(context, part.fileName)
                        AsyncImage(
                            model = File(ImageStorage.imagesDir(context), part.fileName),
                            contentDescription = part.caption.ifBlank { "Imagen" },
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
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
            }
            is ContentPart.GalleryPart -> {
                // El primer índice de este grupo dentro de la lista plana de
                // imágenes; cada imagen del grupo es startIndex + su
                // posición dentro de part.fileNames.
                val startIndex = imageOccurrence
                imageOccurrence += part.fileNames.size
                item(key = "gallery-$partIndex") {
                    GalleryGrid(
                        layout = part.layout,
                        fileNames = part.fileNames,
                        onImageClick = { indexInGroup -> onImageClick(startIndex + indexInGroup) }
                    )
                }
            }
            is ContentPart.VideoPart -> {
                imageOccurrence++
                item(key = "video-$partIndex") {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        NoteVideoPlayer(
                            fileName = part.fileName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(16.dp))
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
            }
        }
    }
}

// Cuadrícula (o carrusel) para un grupo de imágenes agrupadas. El formato
// (GalleryLayout) se elige al insertar el grupo, o toma el que esté
// configurado por defecto en Ajustes. Ya usaba tamaños fijos (.size(...))
// para cada miniatura, así que no tenía el problema de altura variable de
// las imágenes sueltas — no necesitó tocarse para el fix de scroll.
@Composable
internal fun GalleryGrid(layout: GalleryLayout, fileNames: List<String>, onImageClick: (Int) -> Unit) {
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
