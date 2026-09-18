package com.dumb.bouncynotes.ui

// Extraído de NoteEditScreen.kt (que había crecido demasiado) sin cambiar
// nada de la lógica — solo visibilidad `private` -> `internal` (mismo
// paquete `ui`, así que NoteEditScreen.kt lo sigue viendo sin necesitar
// ningún import nuevo).

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.text.input.TextFieldValue
import com.dumb.bouncynotes.data.ContentPart
import com.dumb.bouncynotes.data.GalleryLayout
import com.dumb.bouncynotes.data.buildGalleryTag
import com.dumb.bouncynotes.data.buildImageTag
import com.dumb.bouncynotes.data.buildVideoTag
import com.dumb.bouncynotes.data.parseNoteContent
import kotlinx.coroutines.withTimeoutOrNull

internal sealed class EditSegment {
    data class TextSeg(val value: TextFieldValue) : EditSegment()
    data class ImageSeg(val fileName: String, val caption: String) : EditSegment()
    data class GallerySeg(val layout: GalleryLayout, val fileNames: List<String>, val captions: List<String> = emptyList()) : EditSegment()
    data class VideoSeg(val fileName: String, val caption: String) : EditSegment()
}

internal fun buildEditSegments(content: String): List<EditSegment> {
    val parts = parseNoteContent(content)
    if (parts.isEmpty()) return listOf(EditSegment.TextSeg(TextFieldValue("")))
    // Si dos imágenes quedan una justo al lado de la otra en el contenido guardado
    // (sin ningún carácter entre medio), parseNoteContent no genera ningún TextPart
    // ahí porque literalmente no hay texto que representar. Eso hacía que, al volver
    // a abrir la nota, no existiera ningún campo editable entre esas imágenes y por
    // lo tanto fuera imposible escribir ahí. Insertamos un tramo de texto vacío
    // (solo en memoria, para editar) entre imágenes consecutivas, y al principio/final
    // si la nota empieza o termina con una imagen.
    val segments = mutableListOf<EditSegment>()
    fun isMediaSeg(seg: EditSegment) =
        seg is EditSegment.ImageSeg || seg is EditSegment.GallerySeg || seg is EditSegment.VideoSeg
    parts.forEach { part ->
        when (part) {
            is ContentPart.TextPart -> segments.add(EditSegment.TextSeg(TextFieldValue(part.text)))
            is ContentPart.ImagePart -> {
                if (segments.isEmpty() || isMediaSeg(segments.last())) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.ImageSeg(part.fileName, part.caption))
            }
            is ContentPart.GalleryPart -> {
                if (segments.isEmpty() || isMediaSeg(segments.last())) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.GallerySeg(part.layout, part.fileNames, part.captions))
            }
            is ContentPart.VideoPart -> {
                if (segments.isEmpty() || isMediaSeg(segments.last())) {
                    segments.add(EditSegment.TextSeg(TextFieldValue("")))
                }
                segments.add(EditSegment.VideoSeg(part.fileName, part.caption))
            }
        }
    }
    if (isMediaSeg(segments.last())) {
        segments.add(EditSegment.TextSeg(TextFieldValue("")))
    }
    return segments
}

// El doble-tap para pasar a modo edición se hacía con detectTapGestures en el
// contenedor padre, en el pass por defecto (Main). Ese pass viaja de hijos hacia
// el padre, así que cuando el toque caía sobre un elemento hijo con su propia
// detección de toques (los enlaces del texto vía ClickableText, o antes las
// imágenes con .clickable), el hijo consumía el evento primero y el padre nunca
// llegaba a detectar el doble toque: por eso solo funcionaba tocando "fuera" del
// texto/imagen. Usando el pass Initial (que viaja de padre a hijos, antes de que
// cualquier hijo pueda consumir el evento) el contenedor ve el toque siempre.
internal suspend fun PointerInputScope.detectDoubleTapToEdit(onDoubleTap: () -> Unit) {
    awaitEachGesture {
        awaitFirstDown(pass = PointerEventPass.Initial)
        waitForUpOrCancellation(pass = PointerEventPass.Initial) ?: return@awaitEachGesture
        val secondDown = withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
            awaitFirstDown(pass = PointerEventPass.Initial)
        } ?: return@awaitEachGesture
        secondDown.consume()
        waitForUpOrCancellation(pass = PointerEventPass.Initial)?.consume()
        onDoubleTap()
    }
}

internal fun segmentsToContent(segments: List<EditSegment>): String =
    segments.joinToString("") { seg ->
        when (seg) {
            is EditSegment.TextSeg -> seg.value.text
            is EditSegment.ImageSeg -> buildImageTag(seg.fileName, seg.caption)
            is EditSegment.GallerySeg -> buildGalleryTag(seg.layout, seg.fileNames, seg.captions)
            is EditSegment.VideoSeg -> buildVideoTag(seg.fileName, seg.caption)
        }
    }
